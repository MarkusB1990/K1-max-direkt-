# Slicer-Server für K1 Max Direkt (OrcaSlicer)

Android kann OrcaSlicer nicht selbst ausführen. Deshalb schickt die App das Modell an diesen kleinen
Server in deinem Heimnetz. Er slict mit **OrcaSlicer 2.5.2** und den **offiziellen Creality-K1-Max-Profilen**
von OrcaSlicer (Drucker „Creality K1 Max (0.4 nozzle)“, Prozess „0.20mm Standard @Creality K1Max“,
Filamente „Generic … @Creality“) und gibt den fertigen G-Code zurück – inklusive Vorschaubild fürs
Drucker-Display, Objekt-Ausschluss und dem `START_PRINT`-Makro des K1 Max.

## Variante 1: Home-Assistant-App (empfohlen, läuft immer)

1. Home Assistant → **Einstellungen → Apps → App-Store** → ⋮ → **Repositories**
2. Hinzufügen: `https://github.com/MarkusB1990/K1-max-direkt-#ccr-7592eb33-pjcy8z`
3. App **„K1 Max Slicer“** installieren (der erste Build dauert einige Minuten) und starten.
4. In der Handy-App unter **Einstellungen → Slicer-Server** eintragen: `http://<IP-von-Home-Assistant>:8090`

Optionen in der App-Konfiguration: `token` (Zugangs-Token), `parallel` (gleichzeitige Aufträge), `timeout` (Sekunden).

## Variante 2: Docker (PC, NAS, Raspberry Pi 4/5)

```bash
cd slicer-server
docker compose up -d --build
```

## Was die App einstellen kann

Die Handy-App passt pro Auftrag an: Material (PLA, PETG, ABS, ASA, TPU, PLA-CF), Düsen- und Betttemperatur,
Schichthöhe, Füllung, Wände, Stützen (Baumstützen, nur von der Platte oder überall), Brim, Geschwindigkeit,
Skalierung und Drehung. Alles andere kommt aus den OrcaSlicer-Profilen.

## Optionen (Umgebungsvariablen, Docker)

| Variable          | Standard | Bedeutung                                          |
|-------------------|----------|----------------------------------------------------|
| `SLICER_TOKEN`    | leer     | Wenn gesetzt, muss die App diesen Token mitsenden  |
| `SLICE_PARALLEL`  | `1`      | Wie viele Aufträge gleichzeitig gesliced werden    |
| `SLICE_TIMEOUT`   | `900`    | Abbruch nach so vielen Sekunden                    |
| `MAX_UPLOAD_MB`   | `300`    | Maximale Dateigröße                                |

Eine andere OrcaSlicer-Version lässt sich beim Bauen wählen: `docker build --build-arg ORCA_VERSION=2.5.2 …`

## API

* `GET /health` → `{"status":"ok","slicer":"OrcaSlicer-2.5.2","profiles":[…]}`
* `POST /slice` (multipart): `file` + optional `layer_height`, `infill`, `support` (`none|buildplate|everywhere`),
  `material`, `brim`, `nozzle_temp`, `bed_temp`, `fan`, `perimeters`, `speed` (Prozent), `scale` (Prozent), `rotate` (Grad).
  Antwort: G-Code-Datei, dazu die Header `X-Print-Time`, `X-Filament-Meters` und `X-Filament-Grams`.

Den Server bitte **nicht** ungeschützt ins Internet stellen (höchstens per VPN, z. B. WireGuard/Tailscale).
