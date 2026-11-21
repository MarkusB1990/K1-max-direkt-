"""
K1 Max Direkt – Slicer-Server (OrcaSlicer)

Nimmt STL/3MF/OBJ/STEP-Dateien von der Android-App entgegen, slict sie mit der
OrcaSlicer-Kommandozeile und den offiziellen Creality-K1-Max-Profilen von OrcaSlicer
und liefert den fertigen G-Code zurück.
"""
import asyncio
import json
import os
import re
import shutil
import tempfile
from pathlib import Path

from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import FileResponse
from starlette.background import BackgroundTask

ORCA_BIN = os.environ.get("ORCA_BIN", "/opt/orca/AppRun")
PROFILES = Path(os.environ.get("ORCA_PROFILES", Path(__file__).parent / "orca-profiles"))
TOKEN = os.environ.get("SLICER_TOKEN", "").strip()
MAX_UPLOAD_MB = int(os.environ.get("MAX_UPLOAD_MB", "300"))
TIMEOUT_S = int(os.environ.get("SLICE_TIMEOUT", "900"))
PARALLEL = int(os.environ.get("SLICE_PARALLEL", "1"))

ALLOWED = {".stl", ".3mf", ".obj", ".step", ".stp"}
MATERIALS = {"PLA", "PETG", "ABS", "ASA", "TPU", "PLA_CF"}

# Geschwindigkeiten im Prozessprofil, die mit dem Tempo-Faktor der App skaliert werden
SPEED_KEYS = [
    "outer_wall_speed", "inner_wall_speed", "small_perimeter_speed", "sparse_infill_speed",
    "internal_solid_infill_speed", "top_surface_speed", "gap_infill_speed", "support_speed",
    "support_interface_speed",
]

app = FastAPI(title="K1 Max Direkt Slicer", version="0.2.0")
_sem = asyncio.Semaphore(PARALLEL)


def check_token(authorization: str | None = Header(default=None)):
    if TOKEN and authorization != f"Bearer {TOKEN}":
        raise HTTPException(status_code=401, detail="Ungültiger oder fehlender Token")


async def _run(*args: str, timeout: int, cwd: str | None = None) -> tuple[int, str]:
    proc = await asyncio.create_subprocess_exec(
        *args, cwd=cwd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT
    )
    try:
        out, _ = await asyncio.wait_for(proc.communicate(), timeout=timeout)
    except asyncio.TimeoutError:
        proc.kill()
        raise HTTPException(status_code=504, detail=f"Slicen hat länger als {timeout} s gedauert")
    return proc.returncode, out.decode("utf-8", "replace")


@app.get("/health", dependencies=[Depends(check_token)])
async def health():
    if not Path(ORCA_BIN).exists():
        raise HTTPException(status_code=500, detail=f"OrcaSlicer nicht gefunden: {ORCA_BIN}")
    _, out = await _run(ORCA_BIN, "--help", timeout=60)
    first = next((l.strip().rstrip(":") for l in out.splitlines() if l.startswith("OrcaSlicer")), "OrcaSlicer")
    profiles = (PROFILES / "profiles.txt").read_text().strip().splitlines() if (PROFILES / "profiles.txt").exists() else []
    return {"status": "ok", "slicer": first, "profiles": profiles}


def _clamp(v: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, v))


def _load(name: str) -> dict:
    return json.loads((PROFILES / name).read_text(encoding="utf-8"))


def _scaled(value, factor: float):
    """Skaliert Zahlen in Profilwerten ('200', ['200'], '80%') – Prozentwerte bleiben unverändert."""
    if isinstance(value, list):
        return [_scaled(v, factor) for v in value]
    s = str(value)
    if s.endswith("%"):
        return s
    try:
        return str(round(float(s) * factor))
    except ValueError:
        return value


def build_profiles(work: Path, *, lh: float, infill: int, perimeters: int, support: str, brim: float,
                   material: str, nozzle: int, bed: int, fan: int, speed_factor: float) -> tuple[Path, Path, Path]:
    machine = _load("machine.json")

    process = _load("process.json")
    process.update({
        "name": "K1 Max Direkt",
        "layer_height": f"{lh:.2f}",
        "initial_layer_print_height": f"{max(lh, 0.2):.2f}",
        "sparse_infill_density": f"{infill}%",
        "wall_loops": str(perimeters),
        "enable_support": "1" if support in ("buildplate", "everywhere") else "0",
        "support_type": "tree(auto)",
        "support_on_build_plate_only": "1" if support == "buildplate" else "0",
        "brim_type": "outer_only" if brim > 0 else "no_brim",
        "brim_width": f"{brim:.1f}",
        "curr_bed_type": "Textured PEI Plate",
    })
    for key in SPEED_KEYS:
        if key in process:
            process[key] = _scaled(process[key], speed_factor)

    filament = _load(f"filament_{material}.json")
    filament["name"] = f"K1 Max Direkt {material}"
    filament["nozzle_temperature"] = [str(nozzle)]
    filament["nozzle_temperature_initial_layer"] = [str(nozzle)]
    # Jede Druckplatten-Variante auf dieselbe Betttemperatur setzen – sonst nimmt Orca z. B. die
    # "Cool Plate"-Temperatur (35 °C) oder lehnt eine Platte mit 0 °C als "nicht unterstützt" ab.
    for key in list(filament):
        if re.fullmatch(r".*_plate_temp(_initial_layer)?", key):
            filament[key] = [str(bed)]
    filament["fan_max_speed"] = [str(fan)]
    filament["fan_min_speed"] = [str(fan)]

    paths = []
    for name, cfg in (("machine.json", machine), ("process.json", process), ("filament.json", filament)):
        p = work / name
        p.write_text(json.dumps(cfg, ensure_ascii=False), encoding="utf-8")
        paths.append(p)
    return paths[0], paths[1], paths[2]


def _fmt_seconds(s: float) -> str:
    s = int(round(s))
    h, m, sec = s // 3600, (s % 3600) // 60, s % 60
    return f"{h}h {m}m {sec}s" if h else f"{m}m {sec}s"


def _stats(gcode: Path, statistics: Path | None) -> dict[str, str]:
    stats: dict[str, str] = {}
    size = gcode.stat().st_size
    with gcode.open("rb") as f:
        f.seek(max(0, size - 400_000))
        tail = f.read().decode("utf-8", "replace")
    if m := re.search(r"; estimated printing time \(normal mode\) = (.+)", tail):
        stats["X-Print-Time"] = m.group(1).strip()
    if m := re.search(r"; filament used \[mm\] = ([\d.]+)", tail):
        stats["X-Filament-Meters"] = f"{float(m.group(1)) / 1000:.2f}"
    if m := re.search(r"; (?:total )?filament used \[g\] = ([\d.]+)", tail):
        stats["X-Filament-Grams"] = m.group(1)
    if statistics and statistics.exists() and "X-Print-Time" not in stats:
        try:
            summary = json.loads(statistics.read_text())["summary"]
            stats["X-Print-Time"] = _fmt_seconds(summary["time"])
            stats.setdefault("X-Filament-Grams", f"{summary['weight']:.2f}")
        except Exception:
            pass
    return stats


def _error_text(code: int, log: str, result_json: Path) -> str:
    detail = ""
    if result_json.exists():
        try:
            r = json.loads(result_json.read_text())
            detail = r.get("error_string") or ""
            for plate in r.get("sliced_plates", []):
                if plate.get("warning_message"):
                    detail += f" ({plate['warning_message']})"
        except Exception:
            pass
    hints = {
        206: "Das Modell passt nicht auf die Druckplatte (300 × 300 × 300 mm) – bitte kleiner skalieren.",
    }
    tail = "\n".join(log.strip().splitlines()[-12:])
    return f"OrcaSlicer-Fehler (Code {code}): {hints.get(code, detail or 'unbekannt')}\n{tail}".strip()


@app.post("/slice", dependencies=[Depends(check_token)])
async def slice_model(
    file: UploadFile = File(...),
    layer_height: float = Form(0.2),
    infill: int = Form(15),
    support: str = Form("none"),
    material: str = Form("PLA"),
    brim: float = Form(0),
    nozzle_temp: int = Form(220),
    bed_temp: int = Form(60),
    fan: int = Form(100),
    perimeters: int = Form(2),
    speed: int = Form(100),
    scale: int = Form(100),
    rotate: int = Form(0),
):
    name = Path(file.filename or "modell.stl").name
    ext = Path(name).suffix.lower()
    if ext not in ALLOWED:
        raise HTTPException(status_code=400, detail=f"Dateityp {ext or '?'} wird nicht unterstützt")
    material = material.upper()
    if material not in MATERIALS:
        material = "PLA"

    work = Path(tempfile.mkdtemp(prefix="k1slice_"))
    try:
        safe_stem = re.sub(r"[^\w\-. ]", "_", Path(name).stem)[:80] or "modell"
        src = work / f"{safe_stem}{ext}"
        written = 0
        with src.open("wb") as out:
            while chunk := await file.read(1 << 20):
                written += len(chunk)
                if written > MAX_UPLOAD_MB * 1024 * 1024:
                    raise HTTPException(status_code=413, detail=f"Datei größer als {MAX_UPLOAD_MB} MB")
                out.write(chunk)

        machine, process, filament = build_profiles(
            work,
            lh=_clamp(layer_height, 0.05, 0.32),
            infill=int(_clamp(infill, 0, 100)),
            perimeters=int(_clamp(perimeters, 1, 10)),
            support=support,
            brim=_clamp(brim, 0, 20),
            material=material,
            nozzle=int(_clamp(nozzle_temp, 150, 300)),
            bed=int(_clamp(bed_temp, 0, 110)),
            fan=int(_clamp(fan, 0, 100)),
            speed_factor=_clamp(speed, 30, 200) / 100.0,
        )
        outdir = work / "out"
        outdir.mkdir()
        args = [
            ORCA_BIN, "--datadir", str(work / "orca-data"),
            "--arrange", "1", "--orient", "0", "--slice", "0", "--export-statistics",
            "--load-settings", f"{machine};{process}",
            "--load-filaments", str(filament),
            "--outputdir", str(outdir),
        ]
        if scale != 100:
            args += ["--scale", f"{_clamp(scale, 1, 1000) / 100:.4f}"]
        if rotate % 360:
            args += ["--rotate", str(rotate % 360)]
        args.append(str(src))

        async with _sem:
            code, log = await _run(*args, timeout=TIMEOUT_S, cwd=str(work))
        plate = outdir / "plate_1.gcode"
        if code != 0 or not plate.exists():
            raise HTTPException(status_code=422, detail=_error_text(code, log, outdir / "result.json"))

        result = outdir / f"{safe_stem}.gcode"
        plate.rename(result)
        headers = _stats(result, outdir / "plate_1.statistics.json")
        return FileResponse(
            result,
            media_type="text/x.gcode",
            filename=result.name,
            headers=headers,
            background=BackgroundTask(shutil.rmtree, work, ignore_errors=True),
        )
    except Exception:
        shutil.rmtree(work, ignore_errors=True)
        raise
