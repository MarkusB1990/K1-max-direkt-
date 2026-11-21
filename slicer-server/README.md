# Slicer-Server für K1 Max Direkt

Android kann PrusaSlicer/OrcaSlicer nicht selbst ausführen. Deshalb schickt die App das Modell an diesen
kleinen Server in deinem Heimnetz. Er slict mit **PrusaSlicer 2.7** und einem **Creality-K1-Max-Profil**
(`profiles/k1max.ini`) und gibt den fertigen G-Code zurück.

Der Server läuft auf allem, was Docker kann: PC, NAS (Synology/QNAP/Unraid), Raspberry Pi 4/5 oder
dem Home-Assistant-Host.

## Starten mit Docker (empfohlen)

```bash
cd slicer-server
docker compose up -d --build
```

Danach in der App unter **Einstellungen → Slicer-Server** eintragen:

```
http://<IP-des-Servers>:8090
```

und auf **Speichern & Verbindung testen** tippen.

## Ohne Docker (Linux)

```bash
sudo apt install prusa-slicer python3-venv
python3 -m venv venv && venv/bin/pip install -r requirements.txt
venv/bin/uvicorn app:app --host 0.0.0.0 --port 8090
```

## Optionen (Umgebungsvariablen)

| Variable          | Standard                | Bedeutung                                          |
|-------------------|-------------------------|----------------------------------------------------|
| `SLICER_TOKEN`    | leer                    | Wenn gesetzt, muss die App diesen Token mitsenden  |
| `SLICE_PARALLEL`  | `1`                     | Wie viele Aufträge gleichzeitig gesliced werden    |
| `SLICE_TIMEOUT`   | `900`                   | Abbruch nach so vielen Sekunden                    |
| `MAX_UPLOAD_MB`   | `300`                   | Maximale Dateigröße                                |
| `SLICER_PROFILE`  | `profiles/k1max.ini`    | Eigenes Profil verwenden                           |

## Eigenes Profil

Du kannst in PrusaSlicer am PC ein Profil feinjustieren und über *Datei → Exportieren → Konfiguration
exportieren* als `.ini` speichern. Lege sie in `profiles/` ab und setze `SLICER_PROFILE` darauf.
Wichtig: Start-G-Code `START_PRINT EXTRUDER_TEMP=[first_layer_temperature] BED_TEMP=[first_layer_bed_temperature]`
und `autoemit_temperature_commands = 0` beibehalten, damit die Heiz- und Kalibrier-Makros des K1 Max laufen.

## API

* `GET /health` → `{"status":"ok","slicer":"PrusaSlicer-2.7.2 …"}`
* `POST /slice` (multipart): `file` + optional `layer_height`, `infill`, `support` (`none|buildplate|everywhere`),
  `material` (`PLA|PETG|ABS|ASA|TPU|PLA_CF`), `brim`, `nozzle_temp`, `bed_temp`, `fan`, `perimeters`,
  `speed` (Prozent), `scale` (Prozent), `rotate` (Grad).
  Antwort: G-Code-Datei, dazu die Header `X-Print-Time`, `X-Filament-Meters` und `X-Filament-Grams`.

Den Server bitte **nicht** ungeschützt ins Internet stellen (höchstens per VPN, z. B. WireGuard/Tailscale).
