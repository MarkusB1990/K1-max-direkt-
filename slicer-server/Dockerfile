FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive \
    PYTHONUNBUFFERED=1

RUN apt-get update \
 && apt-get install -y --no-install-recommends prusa-slicer python3 python3-venv ca-certificates \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN python3 -m venv /venv && /venv/bin/pip install --no-cache-dir -r requirements.txt
COPY app.py run.sh ./
COPY profiles ./profiles
RUN chmod +x /app/run.sh

EXPOSE 8090
CMD ["/app/run.sh"]
