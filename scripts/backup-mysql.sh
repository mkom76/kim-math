#!/usr/bin/env bash
set -euo pipefail
umask 077

if [[ $# -gt 0 ]]; then
    echo "Usage: $0" >&2
    exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${MYSQL_BACKUP_ENV_FILE:-$SCRIPT_DIR/../.env}"
if [[ ! -f "$ENV_FILE" ]]; then
    echo "Error: backup config not found: $ENV_FILE" >&2
    exit 1
fi
# shellcheck disable=SC1090
source "$ENV_FILE"

for name in MYSQL_ROOT_PASSWORD MYSQL_DATABASE; do
    if [[ -z "${!name:-}" ]]; then
        echo "Error: $name is required in $ENV_FILE." >&2
        exit 1
    fi
done
for tool in docker gzip; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "Error: required backup tool not found: $tool" >&2
        exit 1
    fi
done

BACKUP_DIR="${MYSQL_BACKUP_DIR:-/home/suhui/backups}"
CONTAINER_NAME="kim-math-mysql"
mkdir -p "$BACKUP_DIR"
TMP_PATH="$(mktemp "$BACKUP_DIR/kim_math_backup_$(date +%Y%m%d_%H%M%S)_XXXXXX")"
trap 'rm -f "$TMP_PATH"' EXIT
BACKUP_PATH="${TMP_PATH}.sql.gz"

echo "MySQL backup started: $(date)"
echo "Creating database dump from $CONTAINER_NAME..."
if ! docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$CONTAINER_NAME" mysqldump \
    -u root \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    "$MYSQL_DATABASE" | gzip > "$TMP_PATH"; then
    echo "Error: database dump or compression failed; deployment must stop." >&2
    exit 1
fi

# Read the complete archive to verify gzip integrity and reject an empty SQL dump.
DUMP_BYTES="$(gzip -cd "$TMP_PATH" | wc -c)"
if [[ "$DUMP_BYTES" -eq 0 ]]; then
    echo "Error: database dump is empty; deployment must stop." >&2
    exit 1
fi
mv "$TMP_PATH" "$BACKUP_PATH"
echo "Local backup verified: $BACKUP_PATH ($(du -h "$BACKUP_PATH" | cut -f1))"

# Clean up only after the new local backup has succeeded.
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'kim_math_backup_*.sql.gz' -mmin +43200 -delete

echo "MySQL backup completed successfully: $BACKUP_PATH"
