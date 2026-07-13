#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "Usage: $0 <--prepare|--ready> /opt/ai-chat/releases/<release-name>" >&2
  exit 64
}

[[ $# -eq 2 ]] || usage
phase="$1"
case "${phase}" in
  --prepare|--ready) ;;
  *) usage ;;
esac

base_dir="${AICHAT_BASE_DIR:-/opt/ai-chat}"
release="$(readlink -f "$2")"
runtime_dir="${base_dir}/runtime"
runtime_env="${runtime_dir}/production.env"
candidate_secrets_dir="${runtime_dir}/.secrets/candidate"
production_secrets_dir="${runtime_dir}/.secrets/production"

[[ -n "${release}" && "${release}" == "${base_dir}/releases/"* ]] \
  || { echo "Release must exist below ${base_dir}/releases" >&2; exit 65; }
release_name="$(basename "${release}")"
[[ "${release_name}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,127}$ ]] \
  || { echo "Release name has an unsafe format" >&2; exit 65; }
export AICHAT_RELEASE="${release_name}"
[[ -f "${release}/compose.cloud.yaml" && -f "${release}/versions.env" ]] \
  || { echo "Release is incomplete" >&2; exit 66; }
[[ "$(stat -c '%u' "${release}")" == 0 ]] \
  || { echo "Release directory must be root-owned" >&2; exit 68; }
[[ -z "$(find "${release}" -xdev -type l -print -quit)" ]] \
  || { echo "Release must not contain symbolic links" >&2; exit 68; }
[[ -z "$(find "${release}" -xdev \( -not -user root -o -perm -002 -o -perm -020 \) -print -quit)" ]] \
  || { echo "Release contains non-root-owned or writable content" >&2; exit 68; }
[[ -f "${runtime_env}" && ! -L "${runtime_env}" ]] \
  || { echo "Production runtime config is missing or is a symlink" >&2; exit 67; }
[[ "$(stat -c '%a:%u' "${runtime_env}")" =~ ^(600|640):0$ ]] \
  || { echo "Production runtime config must be root-owned with mode 600 or 640" >&2; exit 68; }

env_value() {
  local key="$1"
  local matches=()
  mapfile -t matches < <(grep -E "^${key}=" "${runtime_env}" || true)
  (( ${#matches[@]} == 1 )) \
    || { echo "${key} must occur exactly once in ${runtime_env}" >&2; return 69; }
  printf '%s' "${matches[0]#*=}"
}

require_value() {
  local key="$1"
  local expected="$2"
  local actual
  actual="$(env_value "${key}")"
  [[ "${actual}" == "${expected}" ]] \
    || { echo "${key} must be ${expected}, got '${actual:-missing}'" >&2; exit 69; }
}

require_value AICHAT_IMAGE ai-chat-backend:production
require_value AICHAT_SERVICE_NAME ai-chat-production
require_value AICHAT_NETWORK_ALIAS ai-chat-production
require_value DEPLOYMENT_ENVIRONMENT cloud-production
require_value NACOS_SERVER_ADDR nacos-v3:8848
require_value ELASTICSEARCH_SERVER_ADDR elasticsearch-v9:9200
require_value AICHAT_NACOS_DISCOVERY_ENABLED true
require_value AICHAT_RABBITMQ_DYNAMIC true
require_value AICHAT_RABBITMQ_LISTENER_AUTO_STARTUP true
require_value AICHAT_SCHEDULING_ENABLED true
require_value AICHAT_FLYWAY_ENABLED true
require_value AICHAT_ELASTICSEARCH_SYNC_ENABLED true
require_value AICHAT_HEALTH_CHECK_ENABLED false
require_value AICHAT_MODEL_WARMUP_ENABLED true
require_value AICHAT_REQUIRE_NACOS_AUTH true
require_value AICHAT_REQUIRE_ELASTICSEARCH_AUTH true
require_value AICHAT_ELASTICSEARCH_REQUIRED_VERSION 9.3.2
require_value AICHAT_ELASTICSEARCH_REQUIRE_IK true

if [[ "${phase}" == --prepare ]]; then
  require_value AICHAT_PRODUCTION_CONFIRMED false
else
  require_value AICHAT_PRODUCTION_CONFIRMED true
fi

cutover_id="$(env_value AICHAT_CUTOVER_ID)"
[[ "${cutover_id}" =~ ^[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}$ ]] \
  || { echo "AICHAT_CUTOVER_ID must match ^[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}$" >&2; exit 69; }

for port_key in AICHAT_APP_PORT AICHAT_MANAGEMENT_PORT; do
  port="$(env_value "${port_key}")"
  [[ "${port}" =~ ^[0-9]+$ ]] && (( port >= 1024 && port <= 65535 )) \
    || { echo "${port_key} must be a non-privileged TCP port" >&2; exit 69; }
  if ss -ltnH "sport = :${port}" | grep -q .; then
    echo "Production port is already in use: ${port}" >&2
    exit 70
  fi
done
[[ "$(env_value AICHAT_APP_PORT)" != "$(env_value AICHAT_MANAGEMENT_PORT)" ]] \
  || { echo "Production application and management ports must differ" >&2; exit 69; }

for network in middleware-net nacos-net aidiag-telemetry; do
  docker network inspect "${network}" >/dev/null \
    || { echo "Required Docker network is missing: ${network}" >&2; exit 70; }
done

for spec in \
  'nacos_username:400:10001:10001' \
  'nacos_password:400:10001:10001' \
  'elasticsearch_username:400:10001:10001' \
  'elasticsearch_password:400:10001:10001'; do
  IFS=: read -r name mode uid gid <<<"${spec}"
  production_path="${production_secrets_dir}/${name}"
  candidate_path="${candidate_secrets_dir}/${name}"
  [[ -f "${production_path}" && ! -L "${production_path}" && -s "${production_path}" \
    && "$(stat -c '%a:%u:%g' "${production_path}")" == "${mode}:${uid}:${gid}" \
    && "$(wc -l <"${production_path}")" == 0 ]] \
    || { echo "Production Secret is missing, unsafe, or multiline: ${name}" >&2; exit 68; }
  [[ -f "${candidate_path}" && ! -L "${candidate_path}" && -s "${candidate_path}" ]] \
    || { echo "Candidate rollback Secret is missing: ${name}" >&2; exit 68; }
  ! cmp --silent "${production_path}" "${candidate_path}" \
    || { echo "Production and candidate must not share the same Secret: ${name}" >&2; exit 78; }
done

for spec in nacos-v3-candidate:nacos-v3:nacos-net elasticsearch-v9-candidate:elasticsearch-v9:middleware-net; do
  IFS=: read -r container stable_alias network <<<"${spec}"
  [[ "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "${container}" 2>/dev/null || true)" == healthy \
    && "$(docker inspect --format '{{.RestartCount}}' "${container}")" == 0 ]] \
    || { echo "Infrastructure candidate must be healthy with zero restarts: ${container}" >&2; exit 70; }
  docker inspect --format "{{json (index .NetworkSettings.Networks \"${network}\").Aliases}}" "${container}" \
    | grep -Eq "\"${stable_alias}\"" \
    || { echo "Stable production alias is not promoted: ${stable_alias}" >&2; exit 70; }
done

[[ "$(docker image inspect --format '{{index .Config.Labels "aidiag.elasticsearch.version"}}' \
    aidiag/elasticsearch-ik:9.3.2-candidate 2>/dev/null || true)" == 9.3.2 \
  && "$(docker image inspect --format '{{index .Config.Labels "aidiag.analysis-ik.version"}}' \
    aidiag/elasticsearch-ik:9.3.2-candidate 2>/dev/null || true)" == 9.3.2 ]] \
  || { echo "Elasticsearch 9.3.2 + IK image gate failed" >&2; exit 70; }

available_memory_mib="$(awk '/MemAvailable:/ {print int($2 / 1024)}' /proc/meminfo)"
available_disk_mib="$(df -Pm "${base_dir}" | awk 'NR == 2 {print $4}')"
(( available_memory_mib >= 2560 )) \
  || { echo "At least 2560 MiB available memory is required; found ${available_memory_mib}" >&2; exit 70; }
(( available_disk_mib >= 8192 )) \
  || { echo "At least 8192 MiB free disk is required; found ${available_disk_mib}" >&2; exit 70; }

compose=(docker compose --project-name ai-chat-production --project-directory "${release}" \
  -f "${release}/compose.cloud.yaml" \
  -f "${release}/deploy/compose.nacos-auth.yaml" \
  -f "${release}/deploy/compose.elasticsearch-auth.yaml" \
  --env-file "${release}/versions.env" --env-file "${runtime_env}")
"${compose[@]}" config --quiet

release_manifest_sha256="$({
  find "${release}" -type f ! -path '*/.git/*' -print0 \
    | sort -z \
    | xargs -0 sha256sum
} | sha256sum | awk '{print $1}')"

if [[ "${phase}" == --ready ]]; then
  if docker ps --quiet --filter label=com.docker.compose.project=ai-chat-candidate | grep -q .; then
    echo "AI-chat candidate must be stopped before production activation" >&2
    exit 70
  fi

  cutover_dir="${runtime_dir}/cutovers/${cutover_id}"
  writer_evidence="${cutover_dir}/old-writers-stopped.env"
  approval_evidence="${cutover_dir}/approved.env"
  backup_file="${cutover_dir}/ai_agent_db.sql.gz"
  backup_checksum="${backup_file}.sha256"
  for path in "${writer_evidence}" "${approval_evidence}" "${backup_file}" "${backup_checksum}"; do
    [[ -f "${path}" && ! -L "${path}" && -s "${path}" \
      && "$(stat -c '%a:%u' "${path}")" == '600:0' ]] \
      || { echo "Cutover evidence is missing or unsafe: ${path}" >&2; exit 68; }
  done
  grep -qxF "CUTOVER_ID=${cutover_id}" "${writer_evidence}" \
    && grep -qxF 'OLD_PRODUCTION_WRITERS_STOPPED=true' "${writer_evidence}" \
    || { echo "Old production writer-stop evidence does not match the cutover" >&2; exit 78; }
  grep -qxF "CUTOVER_ID=${cutover_id}" "${approval_evidence}" \
    && grep -qxF "RELEASE_MANIFEST_SHA256=${release_manifest_sha256}" "${approval_evidence}" \
    && grep -qxF 'PRODUCTION_ACTIVATION_APPROVED=true' "${approval_evidence}" \
    || { echo "Production approval does not match this release and cutover" >&2; exit 78; }
  (cd "${cutover_dir}" && sha256sum --check "$(basename "${backup_checksum}")" >/dev/null) \
    || { echo "MySQL backup checksum verification failed" >&2; exit 78; }
  gzip --test "${backup_file}" \
    || { echo "MySQL backup gzip integrity verification failed" >&2; exit 78; }
  [[ -f "${release}/src/main/resources/db/migration/V5__add_document_processing_outbox.sql" ]] \
    || { echo "Expected Flyway V5 migration is missing from the release" >&2; exit 78; }
fi

echo "production_preflight=${phase#--}"
echo "cutover_id=${cutover_id}"
echo "release=${release_name}"
echo "release_manifest_sha256=${release_manifest_sha256}"
echo "nacos=3.2.2-authenticated-stable-alias"
echo "elasticsearch=9.3.2-ik-authenticated-stable-alias"
echo "mutation=none"
