"""
K1 Max Direkt – Slicer-Server

Nimmt STL/3MF/OBJ/STEP-Dateien von der Android-App entgegen, slict sie mit
PrusaSlicer (CLI) und dem mitgelieferten Creality-K1-Max-Profil und liefert
den fertigen G-Code zurück.
"""
import asyncio
import os
import re
import shutil
import tempfile
from pathlib import Path

from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import FileResponse
from starlette.background import BackgroundTask

SLICER_BIN = os.environ.get("SLICER_BIN", "prusa-slicer")
PROFILE = Path(os.environ.get("SLICER_PROFILE", Path(__file__).parent / "profiles" / "k1max.ini"))
TOKEN = os.environ.get("SLICER_TOKEN", "").strip()
MAX_UPLOAD_MB = int(os.environ.get("MAX_UPLOAD_MB", "300"))
TIMEOUT_S = int(os.environ.get("SLICE_TIMEOUT", "900"))
PARALLEL = int(os.environ.get("SLICE_PARALLEL", "1"))

ALLOWED = {".stl", ".3mf", ".obj", ".step", ".stp"}

# Materialspezifische Werte, die nicht von der App kommen
MATERIAL_EXTRA = {
    "PLA": {"filament-type": "PLA", "max-volumetric-speed": "21", "filament-density": "1.24"},
    "PLA_CF": {"filament-type": "PLA", "max-volumetric-speed": "15", "filament-density": "1.29"},
    "PETG": {"filament-type": "PETG", "max-volumetric-speed": "12", "filament-density": "1.27"},
    "ABS": {"filament-type": "ABS", "max-volumetric-speed": "18", "filament-density": "1.04"},
    "ASA": {"filament-type": "ASA", "max-volumetric-speed": "18", "filament-density": "1.07"},
    "TPU": {"filament-type": "FLEX", "max-volumetric-speed": "3.5", "filament-density": "1.21",
            "retract-length": "0.4", "retract-speed": "20"},
}

# Basisgeschwindigkeiten aus dem Profil (mm/s) – werden mit dem Geschwindigkeitsfaktor skaliert
BASE_SPEEDS = {
    "perimeter-speed": 200,
    "external-perimeter-speed": 150,
    "infill-speed": 270,
    "solid-infill-speed": 220,
    "top-solid-infill-speed": 150,
    "gap-fill-speed": 100,
    "support-material-speed": 150,
}

app = FastAPI(title="K1 Max Direkt Slicer", version="0.1.0")
_sem = asyncio.Semaphore(PARALLEL)


def check_token(authorization: str | None = Header(default=None)):
    if TOKEN and authorization != f"Bearer {TOKEN}":
        raise HTTPException(status_code=401, detail="Ungültiger oder fehlender Token")


async def _run(*args: str, timeout: int) -> tuple[int, str]:
    proc = await asyncio.create_subprocess_exec(
        *args, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT
    )
    try:
        out, _ = await asyncio.wait_for(proc.communicate(), timeout=timeout)
    except asyncio.TimeoutError:
        proc.kill()
        raise HTTPException(status_code=504, detail=f"Slicen hat länger als {timeout} s gedauert")
    return proc.returncode, out.decode("utf-8", "replace")


@app.get("/health", dependencies=[Depends(check_token)])
async def health():
    if not shutil.which(SLICER_BIN):
        raise HTTPException(status_code=500, detail=f"{SLICER_BIN} nicht gefunden")
    _, out = await _run(SLICER_BIN, "--help", timeout=30)
    first = out.strip().splitlines()[0] if out.strip() else SLICER_BIN
    return {"status": "ok", "slicer": first.strip(), "profile": PROFILE.name}


def _clamp(v: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, v))


def _parse_gcode_stats(path: Path) -> dict[str, str]:
    """Liest Druckzeit/Filament aus den Kommentaren am Ende der G-Code-Datei."""
    stats: dict[str, str] = {}
    size = path.stat().st_size
    with path.open("rb") as f:
        f.seek(max(0, size - 200_000))
        tail = f.read().decode("utf-8", "replace")
    m = re.search(r"; estimated printing time \(normal mode\) = (.+)", tail)
    if m:
        stats["X-Print-Time"] = m.group(1).strip()
    m = re.search(r"; filament used \[mm\] = ([\d.]+)", tail)
    if m:
        stats["X-Filament-Meters"] = f"{float(m.group(1)) / 1000:.2f}"
    m = re.search(r"; filament used \[g\] = ([\d.]+)", tail)
    if m:
        stats["X-Filament-Grams"] = m.group(1)
    return stats


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

        lh = _clamp(layer_height, 0.05, 0.32)
        nozzle = int(_clamp(nozzle_temp, 150, 300))
        bed = int(_clamp(bed_temp, 0, 110))
        fan_pct = int(_clamp(fan, 0, 100))
        factor = _clamp(speed, 30, 200) / 100.0
        result = work / "out" / f"{safe_stem}.gcode"
        result.parent.mkdir()

        args = [
            SLICER_BIN, "--export-gcode",
            "--load", str(PROFILE),
            "--layer-height", f"{lh:.2f}",
            "--first-layer-height", f"{max(lh, 0.2):.2f}",
            "--fill-density", f"{int(_clamp(infill, 0, 100))}%",
            "--perimeters", str(int(_clamp(perimeters, 1, 10))),
            "--temperature", str(nozzle),
            "--first-layer-temperature", str(nozzle),
            "--bed-temperature", str(bed),
            "--first-layer-bed-temperature", str(bed),
            "--min-fan-speed", str(fan_pct),
            "--max-fan-speed", str(fan_pct),
            "--bridge-fan-speed", str(max(fan_pct, 50 if fan_pct else 0)),
            "--brim-width", f"{_clamp(brim, 0, 20):.1f}",
            "--scale", f"{int(_clamp(scale, 1, 1000))}%",
            "--center", "150,150",
            "--ensure-on-bed",
            "--output", str(result),
        ]
        if rotate % 360:
            args += ["--rotate", str(rotate % 360)]
        if support in ("buildplate", "everywhere"):
            args += ["--support-material", "--support-material-auto"]
            if support == "buildplate":
                args += ["--support-material-buildplate-only"]
        for key, base in BASE_SPEEDS.items():
            args += [f"--{key}", str(int(base * factor))]
        for key, value in MATERIAL_EXTRA.get(material.upper(), MATERIAL_EXTRA["PLA"]).items():
            args += [f"--{key}", value]
        args.append(str(src))

        async with _sem:
            code, log = await _run(*args, timeout=TIMEOUT_S)
        if code != 0 or not result.exists():
            tail = "\n".join(line for line in log.strip().splitlines()[-15:])
            raise HTTPException(status_code=422, detail=f"PrusaSlicer-Fehler:\n{tail}")

        headers = _parse_gcode_stats(result)
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
