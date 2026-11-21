FROM ubuntu:24.04

ARG ORCA_VERSION=2.5.2
ENV DEBIAN_FRONTEND=noninteractive \
    PYTHONUNBUFFERED=1 \
    ORCA_BIN=/opt/orca/AppRun \
    ORCA_PROFILES=/app/orca-profiles

# Laufzeit-Bibliotheken für OrcaSlicer (läuft ohne Bildschirm, braucht aber GTK/WebKit/GL-Libs)
RUN apt-get update \
 && apt-get install -y \
      ca-certificates curl git python3 python3-venv \
      libgtk-3-0t64 libwebkit2gtk-4.1-0 libgl1 libegl1 libglu1-mesa \
      libgstreamer1.0-0 libgstreamer-plugins-base1.0-0 libsoup-3.0-0 \
      libsm6 libice6 libx11-6 libxext6 libxkbcommon0 libfontconfig1 libdbus-1-3 libsecret-1-0 \
 && rm -rf /var/lib/apt/lists/*

# OrcaSlicer (offizielles AppImage, entpackt – kein FUSE nötig)
RUN set -eux; \
    case "$(uname -m)" in \
      x86_64)  ASSET="OrcaSlicer_Linux_AppImage_Ubuntu2404_V${ORCA_VERSION}.AppImage" ;; \
      aarch64) ASSET="OrcaSlicer_Linux_AppImage_Ubuntu2404_aarch64_V${ORCA_VERSION}.AppImage" ;; \
      *) echo "Architektur $(uname -m) wird nicht unterstützt"; exit 1 ;; \
    esac; \
    curl -fsSL -o /tmp/orca.AppImage "https://github.com/OrcaSlicer/OrcaSlicer/releases/download/v${ORCA_VERSION}/${ASSET}"; \
    chmod +x /tmp/orca.AppImage; cd /tmp; ./orca.AppImage --appimage-extract > /dev/null; \
    mv /tmp/squashfs-root /opt/orca; rm /tmp/orca.AppImage; \
    /opt/orca/AppRun --help 2>&1 | grep -m1 "OrcaSlicer-"   # bricht ab, falls Bibliotheken fehlen

# Creality-Profile als lesbare JSON-Dateien aus dem Quellcode derselben Version
RUN git clone -q --depth 1 --branch "v${ORCA_VERSION}" --filter=blob:none --sparse \
      https://github.com/OrcaSlicer/OrcaSlicer.git /tmp/orca-src \
 && cd /tmp/orca-src && git sparse-checkout set resources/profiles/Creality resources/profiles/OrcaFilamentLibrary \
 && mv resources/profiles /opt/orca-src-profiles && rm -rf /tmp/orca-src

WORKDIR /app
COPY requirements.txt .
RUN python3 -m venv /venv && /venv/bin/pip install --no-cache-dir -r requirements.txt
COPY orca_profiles.py app.py run.sh ./
RUN python3 orca_profiles.py /opt/orca-src-profiles /app/orca-profiles && chmod +x /app/run.sh

EXPOSE 8090
CMD ["/app/run.sh"]
