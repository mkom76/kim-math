#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
HOOK_DIR="$REPO_ROOT/.git/hooks"
HOOK_PATH="$HOOK_DIR/pre-push"
BACKUP_PATH="$HOOK_DIR/pre-push.before-local-mysql-dump"
MARKER="kim-math-local-mysql-dump"

mkdir -p "$HOOK_DIR"

if [[ -f "$HOOK_PATH" ]] && ! grep -q "$MARKER" "$HOOK_PATH"; then
    if [[ -e "$BACKUP_PATH" ]]; then
        echo "Error: existing backup hook already exists: $BACKUP_PATH" >&2
        echo "Move it away or merge hooks manually." >&2
        exit 1
    fi
    mv "$HOOK_PATH" "$BACKUP_PATH"
    chmod +x "$BACKUP_PATH"
    echo "Existing pre-push hook moved to: $BACKUP_PATH"
fi

cat > "$HOOK_PATH" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

# kim-math-local-mysql-dump
# Auto-created by scripts/install-local-dump-pre-push-hook.sh

REPO_ROOT="$(git rev-parse --show-toplevel)"

"$REPO_ROOT/scripts/dump-mysql-local.sh" --label prepush

PREVIOUS_HOOK="$REPO_ROOT/.git/hooks/pre-push.before-local-mysql-dump"
if [[ -x "$PREVIOUS_HOOK" ]]; then
    exec "$PREVIOUS_HOOK" "$@"
fi
EOF

chmod +x "$HOOK_PATH"
chmod +x "$REPO_ROOT/scripts/dump-mysql-local.sh"

echo "Installed pre-push hook: $HOOK_PATH"
echo ""
echo "Next step:"
echo "  cp .env.local-mysql-dump.example .env.local-mysql-dump"
echo "  # Fill .env.local-mysql-dump with the DataGrip connection values."
echo ""
echo "To test without pushing:"
echo "  ./scripts/dump-mysql-local.sh --label test"
echo ""
echo "To bypass once:"
echo "  MYSQL_DUMP_SKIP=1 git push"
