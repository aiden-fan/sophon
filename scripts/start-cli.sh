#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
exec java -jar client/cli/target/sophon-cli-1.0.0.jar "$@"
