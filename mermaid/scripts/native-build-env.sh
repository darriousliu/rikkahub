#!/usr/bin/env bash
# Shared discovery for terminal and IDE builds; macOS system Python may lack tomllib.
if ! command -v cargo >/dev/null 2>&1; then
    export PATH="${CARGO_HOME:-$HOME/.cargo}/bin:$PATH"
fi

if [[ -n "${MERMAN_PYTHON:-}" ]]; then
    "$MERMAN_PYTHON" -c 'import tomllib' || {
        echo "MERMAN_PYTHON must point to Python 3.11 or newer." >&2
        return 1
    }
else
    for candidate in python3 /opt/homebrew/bin/python3 /usr/local/bin/python3 python3.14 python3.13 python3.12 python3.11; do
        if "$candidate" -c 'import tomllib' >/dev/null 2>&1; then
            MERMAN_PYTHON="$(command -v "$candidate")"
            break
        fi
    done
    if [[ -z "${MERMAN_PYTHON:-}" ]]; then
        echo "Python 3.11+ is required. Set MERMAN_PYTHON to its executable." >&2
        return 1
    fi
fi
export MERMAN_PYTHON
