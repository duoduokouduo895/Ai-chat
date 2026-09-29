#!/usr/bin/env bash
set -Eeuo pipefail

confirmation='--confirm-no-schema-production-upgrade'
if [[ $# -ne 2 || "$2" != "${confirmation}" ]]; then
  echo "Usage: $0 /opt/ai-chat/releases/<release-name> ${confirmation}" >&2
  exit 64
fi

base_dir="${AICHAT_BASE_DIR:-/opt/ai-chat}"
release="$(readlink -f "$1")"
current_link="${base_dir}/current"
runtime_env="${base_dir}/runtime/production.env"
project_name='ai-chat-production'
service_name='ai-chat-backend'

[[ -n "${release}" && "${release}" == "${base_dir}/releases/"* ]] \
  || { echo "Release must exist below ${base_dir}/releases" >&2; exit 65; }
[[ -d "${release}" && -f "${release}/compose.cloud.yaml" && -f "${release}/versions.env" ]] \
  || { echo "Release is incomplete" >&2; exit 66; }
[[ -L "${current_link}" ]] || { echo "Current production release link is missing" >&2; exit 67; }
previous_release="$(readlink -f "${current_link}")"
[[ -n "${previous_release}" && "${previous_release}" == "${base_dir}/releases/"* \
  && "${previous_release}" != "${release}" ]] \
  || { echo "Previous and target production releases must be distinct immutable releases" >&2; exit 67; }
[[ -f "${runtime_env}" && ! -L "${runtime_env}" \
  && "$(stat -c '%a:%u' "${runtime_env}")" =~ ^(600|640):0$ ]] \
  || { echo "Production runtime config is missing or unsafe" >&2; exit 68; }
[[ "$(stat -c '%u' "${release}")" == 0 \
  && -z "$(find "${release}" -xdev -type l -print -quit)" \
  && -z "$(find "${release}" -xdev \( -not -user root -o -perm -002 -o -perm -020 \) -print -quit)" ]] \
  || { echo "Target release must be root-owned, immutable and symlink-free" >&2; exit 68; }

env_value() {
  local key="$1" matches=()
  mapfile -t matches < <(grep -E "^${key}=" "${runtime_env}" || true)
  (( ${#matches[@]} == 1 )) || { echo "${key} must occur exactly once" >&2; return 69; }
  printf '%s' "${matches[0]#*=}"
}

[[ "$(env_value AICHAT_PRODUCTION_CONFIRMED)" == true ]] \
  || { echo "Production must already be approved before an in-place upgrade" >&2; exit 69; }
app_port="$(env_value AICHAT_APP_PORT)"
management_port="$(env_value AICHAT_MANAGEMENT_PORT)"
[[ "${app_port}" =~ ^[0-9]+$ && "${management_port}" =~ ^[0-9]+$ ]] \
  || { echo "Production ports must be numeric" >&2; exit 69; }

migration_dir='src/main/resources/db/migration'
[[ -d "${previous_release}/${migration_dir}" && -d "${release}/${migration_dir}" ]] \
  || { echo "Flyway migration directory is missing" >&2; exit 78; }
diff --brief --recursive --strip-trailing-cr \
  "${previous_release}/${migration_dir}" "${release}/${migration_dir}" >/dev/null \
  || { echo "Database migrations changed; use the full maintenance-window cutover workflow" >&2; exit 78; }

compose_for() {
  local target_release="$1"
  shift
  AICHAT_RELEASE="$(basename "${target_release}")" AICHAT_APP_PORT="${app_port}" \
  docker compose --project-name "${project_name}" --project-directory "${target_release}" \
    -f "${target_release}/compose.cloud.yaml" \
    -f "${target_release}/deploy/compose.elasticsearch-auth.yaml" \
    --env-file "${target_release}/versions.env" --env-file "${runtime_env}" "$@"
}

current_container="$(compose_for "${previous_release}" ps --quiet "${service_name}")"
[[ -n "${current_container}" \
  && "$(docker inspect --format '{{.State.Status}}' "${current_container}")" == running \
  && "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "${current_container}")" == healthy \
  && "$(docker inspect --format '{{.RestartCount}}' "${current_container}")" == 0 ]] \
  || { echo "Current production container must be healthy with zero restarts" >&2; exit 70; }
curl --fail --silent --max-time 10 "http://127.0.0.1:${management_port}/actuator/health" >/dev/null \
  || { echo "Current production management health gate failed" >&2; exit 70; }

compose_for "${release}" config --quiet
compose_for "${release}" build "${service_name}"

next_link="${base_dir}/.current.upgrade-next"
switched=false
completed=false

switch_link() {
  local target="$1"
  [[ ! -e "${next_link}" && ! -L "${next_link}" ]] || unlink "${next_link}"
  ln -s "${target}" "${next_link}"
  mv -Tf "${next_link}" "${current_link}"
}

rollback() {
  set +e
  echo "Production upgrade failed; restoring previous release." >&2
  switch_link "${previous_release}"
  compose_for "${previous_release}" build "${service_name}"
  compose_for "${previous_release}" up -d --no-deps --force-recreate "${service_name}"
  for _ in $(seq 1 36); do
    if curl --fail --silent --max-time 5 "http://127.0.0.1:${management_port}/actuator/health" >/dev/null; then
      echo "Previous production release restored." >&2
      break
    fi
    sleep 5
  done
}

on_exit() {
  local status=$?
  if [[ "${switched}" == true && "${completed}" != true ]]; then
    rollback
  fi
  exit "${status}"
}
trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

switch_link "${release}"
switched=true
compose_for "${release}" up -d --no-deps --force-recreate "${service_name}"

healthy=false
for _ in $(seq 1 36); do
  new_container="$(compose_for "${release}" ps --quiet "${service_name}")"
  if [[ -n "${new_container}" \
    && "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "${new_container}" 2>/dev/null || true)" == healthy \
    && "$(docker inspect --format '{{.RestartCount}}' "${new_container}" 2>/dev/null || true)" == 0 ]] \
    && curl --fail --silent --max-time 5 "http://127.0.0.1:${management_port}/actuator/health" >/dev/null \
    && curl --silent --output /dev/null --max-time 5 "http://127.0.0.1:${app_port}/"; then
    healthy=true
    break
  fi
  sleep 5
done
[[ "${healthy}" == true ]] || { echo "New production release failed its health gate" >&2; exit 70; }

completed=true
trap - EXIT INT TERM
compose_for "${release}" ps
echo "production_upgrade=PASS"
echo "previous_release=$(basename "${previous_release}")"
echo "current_release=$(basename "${release}")"
echo "database_migrations=unchanged"
echo "nginx_mutation=none"
echo "dns_mutation=none"
