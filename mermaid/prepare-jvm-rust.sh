#!/usr/bin/env bash
set -euo pipefail
MERMAID_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$MERMAID_DIR/scripts/native-build-env.sh"
exec "$MERMAN_PYTHON" "$MERMAID_DIR/scripts/build_native.py" jvm "$@"
