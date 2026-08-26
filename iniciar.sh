#!/usr/bin/env bash
# Inicia/detiene/reinicia HCOP JP desde un checkout local (macOS/Linux).
# Equivalente a iniciar.bat en Windows. Usa docker compose directo — ver
# docs/05-operacion/DOCKER.md "Comandos desde un checkout del repositorio".
set -euo pipefail
cd "$(dirname "$0")"

MODE="start"
case "${1:-}" in
  ""|iniciar|start)   MODE="start" ;;
  detener|stop)       MODE="stop" ;;
  reiniciar|restart)  MODE="restart" ;;
  *)
    echo "Uso: $0 [detener|reiniciar]" >&2
    exit 1
    ;;
esac

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker no está instalado o no está en PATH." >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "Docker no está corriendo. Inicie Docker Desktop (o el daemon de Docker)." >&2
  exit 1
fi

if [ ! -f .env ]; then
  echo "Aviso: no existe .env — copie .env.example a .env y complete los secretos" >&2
  echo "antes de iniciar (ver docs/05-operacion/VARIABLES-DE-ENTORNO.md)." >&2
fi

port="5180"
if [ -f .env ]; then
  configured="$(grep -E '^HCOP_PORT=' .env | tail -1 | cut -d= -f2)"
  [ -n "$configured" ] && port="$configured"
fi

open_browser() {
  local url="http://localhost:${port}/"
  if command -v open >/dev/null 2>&1; then
    open "$url" >/dev/null 2>&1 || true
  elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$url" >/dev/null 2>&1 || true
  else
    echo "Abra manualmente: $url"
  fi
}

case "$MODE" in
  stop)
    echo "==> Deteniendo HCOP JP..."
    docker compose down
    echo "HCOP JP fue detenido. Los datos se conservaron."
    ;;
  restart)
    echo "==> Reiniciando HCOP JP..."
    docker compose down || true
    docker compose up --detach --wait
    open_browser
    ;;
  start)
    echo "==> Iniciando HCOP JP..."
    docker compose up --detach --wait
    open_browser
    ;;
esac
