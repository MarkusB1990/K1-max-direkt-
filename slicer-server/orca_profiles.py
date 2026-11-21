"""
Erzeugt aus den mitgelieferten OrcaSlicer-Systemprofilen vollständige ("flache") Profile
für den Creality K1 Max. Die OrcaSlicer-Kommandozeile braucht Profile ohne "inherits"-Kette.

Aufruf:  python3 orca_profiles.py <orca>/resources/profiles <zielordner>
Ergebnis: machine.json, process.json, filament_<MATERIAL>.json, profiles.txt (gewählte Namen)
"""
import json
import re
import sys
from pathlib import Path

MACHINE = "Creality K1 Max (0.4 nozzle)"
PROCESS_PATTERNS = [r"^0\.20mm Standard @Creality K1 ?Max", r"^0\.20mm Standard @Creality K1"]
# Material → Suchmuster für Filamentnamen (erstes Muster mit Treffer gewinnt)
FILAMENTS = {
    "PLA": [r"^Creality Generic PLA @K1", r"^Creality Generic PLA", r"^Generic PLA @System", r"^Generic PLA"],
    "PETG": [r"^Creality Generic PETG @K1", r"^Creality Generic PETG", r"^Generic PETG @System", r"^Generic PETG"],
    "ABS": [r"^Creality Generic ABS @K1", r"^Creality Generic ABS", r"^Generic ABS @System", r"^Generic ABS"],
    "ASA": [r"^Creality Generic ASA @K1", r"^Creality Generic ASA", r"^Generic ASA @System", r"^Generic ASA"],
    "TPU": [r"^Creality Generic TPU @K1", r"^Creality Generic TPU", r"^Generic TPU @System", r"^Generic TPU"],
    "PLA_CF": [r"^Creality Generic PLA-CF @K1", r"^Creality Generic PLA-CF", r"^Generic PLA-CF @System", r"^Generic PLA-CF"],
}


def load_index(root: Path) -> dict[str, dict]:
    index: dict[str, dict] = {}
    for f in root.rglob("*.json"):
        try:
            data = json.loads(f.read_text(encoding="utf-8"))
        except Exception:
            continue
        if isinstance(data, dict) and "name" in data and "type" in data:
            # Hersteller-Ordner bevorzugen wir in der Reihenfolge, in der sie gefunden werden;
            # Creality-Profile überschreiben gleichnamige aus anderen Ordnern nicht.
            index.setdefault(data["name"], {**data, "__file": str(f)})
    return index


def flatten(name: str, index: dict[str, dict], seen=None) -> dict:
    seen = seen or set()
    if name in seen:
        raise ValueError(f"Zyklische Vererbung bei {name}")
    seen.add(name)
    if name not in index:
        raise KeyError(f"Profil nicht gefunden: {name}")
    node = index[name]
    parent = node.get("inherits")
    base = flatten(parent, index, seen) if parent else {}
    merged = {**base, **{k: v for k, v in node.items() if not k.startswith("__")}}
    merged.pop("inherits", None)
    return merged


def pick(index: dict[str, dict], kind: str, patterns: list[str], must_match_printer: str | None = None) -> str:
    names = sorted(n for n, d in index.items() if d.get("type") == kind and d.get("instantiation", "true") != "false")
    for pat in patterns:
        hits = [n for n in names if re.search(pat, n)]
        if must_match_printer:
            pref = [n for n in hits if must_match_printer in json.dumps(index[n].get("compatible_printers", []))]
            hits = pref or hits
        if hits:
            return hits[0]
    raise KeyError(f"Kein {kind}-Profil für {patterns}. Kandidaten: {[n for n in names if 'K1' in n][:40]}")


def finalize(cfg: dict, kind: str, name: str) -> dict:
    cfg = dict(cfg)
    cfg["type"] = kind
    cfg["name"] = name
    cfg["from"] = "User"
    # Leere Liste = mit jedem Drucker kompatibel (vermeidet Kompatibilitätsfehler im CLI)
    cfg["compatible_printers"] = []
    cfg["compatible_printers_condition"] = ""
    cfg.pop("compatible_prints", None)
    cfg["compatible_prints_condition"] = ""
    return cfg


def main():
    root, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    index = load_index(root)
    chosen = []

    machine = finalize(flatten(MACHINE, index), "machine", MACHINE)
    (out / "machine.json").write_text(json.dumps(machine, indent=1, ensure_ascii=False))
    chosen.append(f"machine: {MACHINE}")

    pname = pick(index, "process", PROCESS_PATTERNS, MACHINE)
    (out / "process.json").write_text(json.dumps(finalize(flatten(pname, index), "process", pname), indent=1, ensure_ascii=False))
    chosen.append(f"process: {pname}")

    for mat, pats in FILAMENTS.items():
        try:
            fname = pick(index, "filament", pats, MACHINE)
        except KeyError as e:
            chosen.append(f"filament {mat}: FEHLT ({e})")
            continue
        cfg = finalize(flatten(fname, index), "filament", fname)
        (out / f"filament_{mat}.json").write_text(json.dumps(cfg, indent=1, ensure_ascii=False))
        chosen.append(f"filament {mat}: {fname}")

    (out / "profiles.txt").write_text("\n".join(chosen) + "\n")
    print("\n".join(chosen))


if __name__ == "__main__":
    main()
