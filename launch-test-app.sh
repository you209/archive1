#!/usr/bin/env bash
set -euo pipefail

PORT="${1:-8080}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${ROOT_DIR}/quick-test-app"

if [[ ! -f "${APP_DIR}/index.html" ]]; then
  echo "Error: ${APP_DIR}/index.html not found"
  exit 1
fi

echo "Starting Quick Test App at http://localhost:${PORT}"
echo "Press Ctrl+C to stop."
cd "${APP_DIR}"
python3 -m http.server "${PORT}"
