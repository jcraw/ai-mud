#!/usr/bin/env bash
# tools/smoke_commands.sh — headless look/take/inventory/attack (MUD-038 / E1)
# Isolated MUD_DATA_DIR. No live OpenAI. Not on --core. See docs/COMMAND_SMOKE.md.
#
# Usage:
#   ./tools/smoke_commands.sh
#   SMOKE_TIMEOUT=180 ./tools/smoke_commands.sh
#
# Exit: 0 PASS; non-zero FAIL <step>: <reason> on stderr (from CommandSmokeKt).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

SMOKE_TIMEOUT="${SMOKE_TIMEOUT:-180}"
MUD_DATA_DIR="$(mktemp -d "${TMPDIR:-/tmp}/mud-smoke.XXXXXX")"
export MUD_DATA_DIR

cleanup() {
  if [[ -n "${MUD_DATA_DIR:-}" && -d "${MUD_DATA_DIR}" ]]; then
    rm -rf "${MUD_DATA_DIR}"
  fi
}
trap cleanup EXIT

if [[ -z "${MUD_DATA_DIR}" ]]; then
  echo "FAIL setup: MUD_DATA_DIR unset" >&2
  exit 1
fi

run_smoke() {
  env -u OPENAI_API_KEY ./gradlew :app:run -PcommandSmoke=1 --no-daemon --console=plain
}

if command -v timeout >/dev/null 2>&1; then
  timeout "${SMOKE_TIMEOUT}" env -u OPENAI_API_KEY \
    ./gradlew :app:run -PcommandSmoke=1 --no-daemon --console=plain
else
  run_smoke
fi
