#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if [[ ! -x producer/.venv/bin/python ]]; then
  echo "Ambiente Python não encontrado. Execute uma vez:" >&2
  echo "  cd producer && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt" >&2
  exit 1
fi
exec producer/.venv/bin/python producer/simulator.py "$@"
