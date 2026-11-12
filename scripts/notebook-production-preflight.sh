#!/usr/bin/env bash
# Read-only operational checks. Never print credentials or query business tables.
set -euo pipefail
root=/home/suhui/kim-math
cd "$root"
[[ "${NOTEBOOK_EXPECTED_SERVER_SHA:-}" =~ ^[a-f0-9]{40}$ ]]
[[ "${NOTEBOOK_SOURCE_SHA:-}" =~ ^[a-f0-9]{40}$ ]]
case "$(git remote get-url origin)" in
  https://github.com/mkom76/kim-math.git|git@github.com:mkom76/kim-math.git) ;;
  *) echo 'Unexpected production repository'; exit 1 ;;
esac
current_sha=$(git rev-parse HEAD)
printf 'server_sha=%s\n' "$current_sha"
[[ "$current_sha" == "$NOTEBOOK_EXPECTED_SERVER_SHA" ]]
[[ -z "$(git status --porcelain --untracked-files=no)" ]]
test -r .env
command -v python3 >/dev/null
docker-compose --version
docker-compose -f docker-compose.prod.yml config --quiet
for service in kim-math-backend kim-math-frontend kim-math-mysql; do
  health=$(docker inspect --format '{{.State.Health.Status}}' "$service")
  printf '%s_health=%s\n' "$service" "$health"
  [[ "$health" == healthy ]]
done
if docker exec kim-math-backend sh -c 'test -n "$OPENAI_API_KEY"'; then
  echo 'openai_key_configured=true'
else
  echo 'openai_key_configured=false'
fi
printf 'backend_uid=%s\n' "$(docker exec kim-math-backend id -u)"
printf 'backend_gid=%s\n' "$(docker exec kim-math-backend id -g)"
printf 'compose_project=%s\n' "$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' kim-math-backend)"
compose_file="/tmp/notebook-compose-${NOTEBOOK_SOURCE_SHA}.yml"
trap 'rm -f -- "$compose_file"' EXIT
curl --fail --silent --show-error --location --max-time 30 \
  "https://raw.githubusercontent.com/mkom76/kim-math/${NOTEBOOK_SOURCE_SHA}/docker-compose.prod.yml" -o "$compose_file"
docker-compose --project-directory "$root" --env-file "$root/.env" -f "$compose_file" config --quiet
df -Pm "$root" | tail -n 1 | awk '{print "filesystem_available_mb=" $4}'
echo 'notebook_preflight=passed'
