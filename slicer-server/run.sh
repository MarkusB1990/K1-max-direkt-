#!/bin/sh
# Startskript: übernimmt in Home Assistant die App-Optionen aus /data/options.json als Umgebungsvariablen.
set -e
if [ -f /data/options.json ]; then
  eval "$(python3 - <<'PY'
import json, shlex
o = json.load(open("/data/options.json"))
env = {
    "SLICER_TOKEN": o.get("token") or "",
    "SLICE_PARALLEL": str(o.get("parallel", 1)),
    "SLICE_TIMEOUT": str(o.get("timeout", 900)),
}
print("\n".join(f"export {k}={shlex.quote(v)}" for k, v in env.items()))
PY
)"
fi
echo "K1 Max Slicer startet auf Port 8090 (parallel=${SLICE_PARALLEL:-1}, Token: $([ -n "${SLICER_TOKEN:-}" ] && echo ja || echo nein))"
cd /app
exec /venv/bin/uvicorn app:app --host 0.0.0.0 --port 8090
