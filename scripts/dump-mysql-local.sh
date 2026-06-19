#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="${MYSQL_DUMP_ENV_FILE:-$REPO_ROOT/.env.local-mysql-dump}"
LABEL=""

usage() {
    cat <<'EOF'
Usage: dump-mysql-local.sh [options]

Options:
  --label <label>      Add a sanitized suffix to the dump filename.
  --env-file <path>    Use a custom env file instead of .env.local-mysql-dump.
  -h, --help           Show this help.

The script reads local-only settings from .env.local-mysql-dump.
Copy .env.local-mysql-dump.example first and fill values from DataGrip.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --label)
            LABEL="${2:-}"
            shift 2
            ;;
        --env-file)
            ENV_FILE="${2:-}"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ "${MYSQL_DUMP_SKIP:-0}" == "1" ]]; then
    echo "MySQL dump skipped because MYSQL_DUMP_SKIP=1."
    exit 0
fi

if [[ ! -f "$ENV_FILE" ]]; then
    echo "Error: local dump config not found: $ENV_FILE" >&2
    echo "Copy .env.local-mysql-dump.example to .env.local-mysql-dump and fill DataGrip connection values." >&2
    exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

if [[ "${MYSQL_DUMP_SKIP:-0}" == "1" ]]; then
    echo "MySQL dump skipped because MYSQL_DUMP_SKIP=1."
    exit 0
fi

require_env() {
    local name="$1"
    if [[ -z "${!name:-}" ]]; then
        echo "Error: $name is required in $ENV_FILE." >&2
        exit 1
    fi
}

find_mysqldump() {
    if [[ -n "${MYSQLDUMP_BIN:-}" && -x "$MYSQLDUMP_BIN" ]]; then
        echo "$MYSQLDUMP_BIN"
        return
    fi

    if command -v mysqldump >/dev/null 2>&1; then
        command -v mysqldump
        return
    fi

    for candidate in \
        /opt/homebrew/opt/mysql-client/bin/mysqldump \
        /usr/local/opt/mysql-client/bin/mysqldump \
        /opt/homebrew/bin/mysqldump \
        /usr/local/bin/mysqldump; do
        if [[ -x "$candidate" ]]; then
            echo "$candidate"
            return
        fi
    done

    echo "Error: mysqldump not found. Install mysql-client or set MYSQLDUMP_BIN." >&2
    exit 1
}

resolve_path() {
    local path="$1"
    if [[ "$path" = /* ]]; then
        echo "$path"
    else
        echo "$REPO_ROOT/$path"
    fi
}

sanitize_label() {
    local raw="$1"
    local safe="${raw//[^a-zA-Z0-9_.-]/_}"
    safe="${safe##_}"
    safe="${safe%%_}"
    echo "$safe"
}

require_env MYSQL_DUMP_HOST
require_env MYSQL_DUMP_DATABASE
require_env MYSQL_DUMP_USER

MYSQL_DUMP_PORT="${MYSQL_DUMP_PORT:-3306}"
MYSQL_DUMP_OUTPUT_DIR="${MYSQL_DUMP_OUTPUT_DIR:-local-mysql-dumps}"
MYSQL_DUMP_RETENTION_DAYS="${MYSQL_DUMP_RETENTION_DAYS:-30}"

MYSQLDUMP="$(find_mysqldump)"
OUTPUT_DIR="$(resolve_path "$MYSQL_DUMP_OUTPUT_DIR")"
mkdir -p "$OUTPUT_DIR"

SSH_TARGET=""
SSH_CONTROL_SOCKET=""
if [[ -n "${MYSQL_DUMP_SSH_HOST:-}" ]]; then
    MYSQL_DUMP_SSH_PORT="${MYSQL_DUMP_SSH_PORT:-22}"
    MYSQL_DUMP_REMOTE_HOST="${MYSQL_DUMP_REMOTE_HOST:-127.0.0.1}"
    MYSQL_DUMP_REMOTE_PORT="${MYSQL_DUMP_REMOTE_PORT:-3306}"
    MYSQL_DUMP_LOCAL_PORT="${MYSQL_DUMP_LOCAL_PORT:-33306}"

    if [[ -n "${MYSQL_DUMP_SSH_USER:-}" ]]; then
        SSH_TARGET="${MYSQL_DUMP_SSH_USER}@${MYSQL_DUMP_SSH_HOST}"
    else
        SSH_TARGET="$MYSQL_DUMP_SSH_HOST"
    fi

    SSH_CONTROL_SOCKET="/tmp/kim-math-mysql-dump-${$}.sock"
    SSH_ARGS=(
        -f -N
        -M -S "$SSH_CONTROL_SOCKET"
        -o ExitOnForwardFailure=yes
        -L "${MYSQL_DUMP_LOCAL_PORT}:${MYSQL_DUMP_REMOTE_HOST}:${MYSQL_DUMP_REMOTE_PORT}"
        -p "$MYSQL_DUMP_SSH_PORT"
    )
    if [[ -n "${MYSQL_DUMP_SSH_KEY:-}" ]]; then
        SSH_ARGS+=(-i "$MYSQL_DUMP_SSH_KEY")
    fi

    echo "Opening SSH tunnel: localhost:${MYSQL_DUMP_LOCAL_PORT} -> ${MYSQL_DUMP_REMOTE_HOST}:${MYSQL_DUMP_REMOTE_PORT}"
    ssh "${SSH_ARGS[@]}" "$SSH_TARGET"
    trap 'ssh -S "$SSH_CONTROL_SOCKET" -O exit "$SSH_TARGET" >/dev/null 2>&1 || true' EXIT

    MYSQL_DUMP_HOST="127.0.0.1"
    MYSQL_DUMP_PORT="$MYSQL_DUMP_LOCAL_PORT"
fi

DATE="$(date +%Y%m%d_%H%M%S)"
SAFE_LABEL="$(sanitize_label "$LABEL")"
if [[ -n "$SAFE_LABEL" ]]; then
    OUTPUT_FILE="kim_math_local_dump_${DATE}_${SAFE_LABEL}.sql.gz"
else
    OUTPUT_FILE="kim_math_local_dump_${DATE}.sql.gz"
fi
OUTPUT_PATH="$OUTPUT_DIR/$OUTPUT_FILE"
TMP_PATH="${OUTPUT_PATH}.tmp"

DUMP_ARGS=(
    -h "$MYSQL_DUMP_HOST"
    -P "$MYSQL_DUMP_PORT"
    -u "$MYSQL_DUMP_USER"
    --protocol=TCP
    --single-transaction
    --routines
    --triggers
    --events
)

if [[ -n "${MYSQL_DUMP_DEFAULTS_FILE:-}" ]]; then
    DUMP_ARGS=(--defaults-extra-file="$MYSQL_DUMP_DEFAULTS_FILE" "${DUMP_ARGS[@]}")
elif [[ -n "${MYSQL_DUMP_PASSWORD:-}" ]]; then
    export MYSQL_PWD="$MYSQL_DUMP_PASSWORD"
fi

if [[ -n "${MYSQL_DUMP_EXTRA_ARGS:-}" ]]; then
    # Intentionally supports simple space-separated flags only.
    read -r -a EXTRA_ARGS <<< "$MYSQL_DUMP_EXTRA_ARGS"
    DUMP_ARGS+=("${EXTRA_ARGS[@]}")
fi

DUMP_ARGS+=("$MYSQL_DUMP_DATABASE")

echo "========================================="
echo "Local MySQL Dump Started: $(date)"
echo "Host: ${MYSQL_DUMP_HOST}:${MYSQL_DUMP_PORT}"
echo "Database: $MYSQL_DUMP_DATABASE"
echo "Output: $OUTPUT_PATH"
echo "========================================="

"$MYSQLDUMP" "${DUMP_ARGS[@]}" | gzip > "$TMP_PATH"

if [[ ! -s "$TMP_PATH" ]]; then
    rm -f "$TMP_PATH"
    echo "Error: dump output is empty." >&2
    exit 1
fi

mv "$TMP_PATH" "$OUTPUT_PATH"
SIZE="$(du -h "$OUTPUT_PATH" | cut -f1)"
echo "Dump created: $OUTPUT_PATH ($SIZE)"

if [[ "$MYSQL_DUMP_RETENTION_DAYS" =~ ^[0-9]+$ ]]; then
    find "$OUTPUT_DIR" -name "kim_math_local_dump_*.sql.gz" -mtime +"$MYSQL_DUMP_RETENTION_DAYS" -delete
fi

echo "Local MySQL Dump Completed: $(date)"
