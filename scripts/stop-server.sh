#!/usr/bin/env bash
set -euo pipefail
HOME_DIR="${SOPHON_HOME:-$HOME/.sophon}"
PID_FILE="$HOME_DIR/data/server.pid"
if [[ ! -f "$PID_FILE" ]]; then
  echo "No pid file at $PID_FILE" >&2
  exit 1
fi
PID="$(cat "$PID_FILE")"
kill "$PID" 2>/dev/null || true
rm -f "$PID_FILE"
echo "Stopped PID $PID"
