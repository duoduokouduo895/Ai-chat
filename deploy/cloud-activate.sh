#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <candidate|production> /opt/ai-chat/releases/<release-name>" >&2
  exit 64
fi

mode="$1"
case "${mode}" in
  candidate|production) ;;
  *) echo "Mode must be candidate or production" >&2; exit 64 ;;
esac

base_dir="${AICHAT_BASE_DIR:-/opt/ai-chat}"
release="$(readlink -f "$2")"
runtime_dir="${base_dir}/runtime"
runtime_env="${runtime_dir}/${mode}.env"
active_link="${base_dir}/$([[ "${mode}" == candidate ]] && echo candidate || echo current)"
next_link="${base_dir}/.$([[ "${mode}" == candidate ]] && echo candidate || echo current).next"
project_name="ai-chat-${mode}"
log_dir="${base_dir}/logs"
startup_log="${log_dir}/startup-failures.log"
startup_log_max_bytes=$((5 * 1024 * 1024))

[[ -n "${release}" && "${release}" == "${base_dir}/releases/"* ]] \
  || { echo "Release must exist below ${base_dir}/releases" >&2; exit 65; }
[[ -f "${release}/compose.cloud.yaml" && -f "${release}/versions.env" ]] \
  || { echo "Release is incomplete" >&2; exit 66; }
[[ -f "${runtime_env}" ]] || { echo "Runtime config is missing: ${runtime_env}" >&2; exit 67; }
[[ "$(stat -c '%a' "${runtime_env}")" =~ ^(600|640)$ ]] \
  || { echo "Runtime config must use mode 600 or 640" >&2; exit 68; }

env_value_from() {
  local file="$1"
  local key="$2"
  local matches=()
  mapfile -t matches < <(grep -E "^${key}=" "${file}" || true)
  (( ${#matches[@]} <= 1 )) || { echo "${key} must occur at most once in ${file}" >&2; return 69; }
  if (( ${#matches[@]} == 1 )); then
    printf '%s' "${matches[0]#*=}"
  fi
}

env_value() {
  local key="$1"
  local value
  value="$(env_value_from "${runtime_env}" "${key}")"
  if [[ -z "${value}" ]]; then
    value="$(env_value_from "${release}/versions.env" "${key}")"
  fi
  printf '%s' "${value}"
}

require_value() {
  local key="$1"
  local expected="$2"
  local actual
  actual="$(env_value "${key}")"
  [[ "${actual}" == "${expected}" ]] \
    || { echo "${key} must be ${expected} for ${mode}, got '${actual:-missing}'" >&2; exit 69; }
}

if [[ "${mode}" == candidate ]]; then
  expected_image="ai-chat-backend:candidate"
  expected_service="ai-chat-candidate"
  expected_environment="cloud-candidate"
  expected_ops_log="/var/log/ai-chat/operations.jsonl"
  expected_nacos_server="nacos-v3-candidate:8848"
  require_value AICHAT_REQUIRE_NACOS_AUTH true
  require_value AICHAT_NACOS_DISCOVERY_ENABLED true
  require_value AICHAT_RABBITMQ_DYNAMIC false
  require_value AICHAT_RABBITMQ_LISTENER_AUTO_STARTUP false
  require_value AICHAT_SCHEDULING_ENABLED false
  require_value AICHAT_FLYWAY_ENABLED false
  require_value AICHAT_ELASTICSEARCH_SYNC_ENABLED false
  require_value AICHAT_MODEL_WARMUP_ENABLED false
else
  expected_image="ai-chat-backend:production"
  expected_service="ai-chat-production"
  expected_environment="cloud-production"
  expected_ops_log="/var/log/ai-chat/production-operations.jsonl"
  expected_nacos_server="nacos:8848"
  require_value AICHAT_PRODUCTION_CONFIRMED true
  require_value AICHAT_REQUIRE_NACOS_AUTH true
  require_value AICHAT_NACOS_DISCOVERY_ENABLED true
  require_value AICHAT_RABBITMQ_DYNAMIC true
  require_value AICHAT_RABBITMQ_LISTENER_AUTO_STARTUP true
  require_value AICHAT_SCHEDULING_ENABLED true
  require_value AICHAT_FLYWAY_ENABLED true
  require_value AICHAT_ELASTICSEARCH_SYNC_ENABLED true
  require_value AICHAT_MODEL_WARMUP_ENABLED true
fi

require_value AICHAT_IMAGE "${expected_image}"
require_value AICHAT_SERVICE_NAME "${expected_service}"
require_value AICHAT_NETWORK_ALIAS "${expected_service}"
require_value DEPLOYMENT_ENVIRONMENT "${expected_environment}"
require_value AICHAT_OPS_LOG_FILE "${expected_ops_log}"
require_value AICHAT_HEALTH_CHECK_ENABLED false
require_value NACOS_SERVER_ADDR "${expected_nacos_server}"

app_port="$(env_value AICHAT_APP_PORT)"
if [[ -z "${app_port}" && "${mode}" == candidate ]]; then
  app_port="$(env_value AICHAT_CANDIDATE_PORT)"
fi
management_port="$(env_value AICHAT_MANAGEMENT_PORT)"
[[ "${app_port}" =~ ^[0-9]+$ && "${management_port}" =~ ^[0-9]+$ ]] \
  || { echo "Application and management ports must be numeric" >&2; exit 69; }
(( app_port >= 1024 && app_port <= 65535 && management_port >= 1024 && management_port <= 65535 )) \
  || { echo "Application and management ports must be between 1024 and 65535" >&2; exit 69; }
[[ "${app_port}" != "${management_port}" ]] \
  || { echo "Application and management ports must differ" >&2; exit 69; }
export AICHAT_APP_PORT="${app_port}"
export AICHAT_RELEASE="$(basename "${release}")"

for network in middleware-net nacos-net aidiag-telemetry; do
  docker network inspect "${network}" >/dev/null
done

aidiag_secrets_dir="$(env_value AIDIAG_SECRETS_DIR)"
aidiag_secrets_dir="${aidiag_secrets_dir:-/opt/aidiag/runtime/.secrets}"
[[ -s "${aidiag_secrets_dir}/aidiag_read_api_secret" ]] \
  || { echo "AIDiag read secret is missing or empty" >&2; exit 69; }

require_nacos_auth="$(env_value AICHAT_REQUIRE_NACOS_AUTH)"
require_nacos_auth="${require_nacos_auth:-false}"
[[ "${require_nacos_auth}" == true || "${require_nacos_auth}" == false ]] \
  || { echo "AICHAT_REQUIRE_NACOS_AUTH must be true or false" >&2; exit 69; }

if [[ "${require_nacos_auth}" == true ]]; then
  [[ -f "${release}/deploy/compose.nacos-auth.yaml" ]] \
    || { echo "Nacos auth overlay is missing" >&2; exit 69; }
  aichat_secrets_dir="$(env_value AICHAT_SECRETS_DIR)"
  aichat_secrets_dir="${aichat_secrets_dir:-${runtime_dir}/.secrets}"
  for secret_name in nacos_username nacos_password; do
    secret_file="${aichat_secrets_dir}/${secret_name}"
    [[ -s "${secret_file}" ]] || { echo "Nacos secret is missing or empty: ${secret_file}" >&2; exit 69; }
    [[ "$(stat -c '%a' "${secret_file}")" =~ ^(400|440|600|640)$ ]] \
      || { echo "Nacos secret must use mode 400, 440, 600 or 640: ${secret_file}" >&2; exit 69; }
  done
fi

install -d -o 10001 -g 10001 -m 0750 "${log_dir}"

compose_for() {
  local target_release="$1"
  shift
  local target_files=()
  if [[ -f "${target_release}/compose.cloud.yaml" ]]; then
    target_files=(-f "${target_release}/compose.cloud.yaml")
    if [[ "${require_nacos_auth}" == true ]]; then
      target_files+=(-f "${target_release}/deploy/compose.nacos-auth.yaml")
    fi
  elif [[ "${mode}" == candidate && -f "${target_release}/compose.candidate.yaml" ]]; then
    # One-release compatibility seam: the first authenticated Nacos 3 candidate can
    # still restore the currently active pre-module Stage 5 candidate if its health
    # gate fails. The legacy Compose file owns its old Nacos/auth semantics.
    target_files=(-f "${target_release}/compose.candidate.yaml")
  else
    echo "Release does not support the required ${mode} deployment mode: ${target_release}" >&2
    return 66
  fi
  docker compose --project-name "${project_name}" --project-directory "${target_release}" \
    "${target_files[@]}" --env-file "${target_release}/versions.env" --env-file "${runtime_env}" "$@"
}

switch_active_link() {
  local target_release="$1"
  if [[ -e "${active_link}" && ! -L "${active_link}" ]]; then
    echo "Active path exists and is not a symlink: ${active_link}" >&2
    return 70
  fi
  if [[ -e "${next_link}" || -L "${next_link}" ]]; then
    [[ -L "${next_link}" ]] || { echo "Next-link path exists and is not a symlink: ${next_link}" >&2; return 70; }
    unlink "${next_link}"
  fi
  ln -s "${target_release}" "${next_link}"
  mv -Tf "${next_link}" "${active_link}"
}

capture_startup_failure() {
  local target_release="$1"
  local temporary_log
  temporary_log="$(mktemp "${log_dir}/.startup-failure.XXXXXX")"
  {
    printf '%s mode=%s release=%s event=ai_chat_startup_failure\n' \
      "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${mode}" "$(basename "${target_release}")"
    compose_for "${target_release}" logs --no-color --timestamps --tail 500 ai-chat-backend 2>&1 || true
  } | sed -E \
      -e 's/((password|passwd|token|secret|api[_-]?key|authorization)[=:][[:space:]]*)[^[:space:],;]+/\1[REDACTED]/Ig' \
      -e 's#(Bearer|Basic)[[:space:]]+[A-Za-z0-9._~+/-]+=*#\1 [REDACTED]#g' \
      -e 's#(://)[^/@[:space:]]+:[^/@[:space:]]+@#\1[REDACTED]@#g' \
      > "${temporary_log}"

  if [[ ! -e "${startup_log}" ]]; then
    install -o 10001 -g 10001 -m 0640 /dev/null "${startup_log}"
  fi
  cat "${temporary_log}" >> "${startup_log}"
  rm -f "${temporary_log}"
  chown 10001:10001 "${startup_log}"
  chmod 0640 "${startup_log}"

  if (( $(stat -c '%s' "${startup_log}") > startup_log_max_bytes )); then
    temporary_log="$(mktemp "${log_dir}/.startup-rotate.XXXXXX")"
    tail -c "${startup_log_max_bytes}" "${startup_log}" > "${temporary_log}"
    chown 10001:10001 "${temporary_log}"
    chmod 0640 "${temporary_log}"
    mv -f "${temporary_log}" "${startup_log}"
  fi
}

restore_previous() {
  local previous_release="$1"
  local failed_release="$2"
  if [[ -n "${previous_release}" ]]; then
    switch_active_link "${previous_release}"
    compose_for "${previous_release}" build ai-chat-backend \
      && compose_for "${previous_release}" up -d --remove-orphans ai-chat-backend \
      || true
  else
    compose_for "${failed_release}" down --remove-orphans || true
    [[ ! -L "${active_link}" ]] || unlink "${active_link}"
  fi
}

compose_for "${release}" config --quiet
compose_for "${release}" build ai-chat-backend

previous_release=""
if [[ -L "${active_link}" ]]; then
  previous_release="$(readlink -f "${active_link}")"
fi

switch_active_link "${release}"

if ! compose_for "${release}" up -d --remove-orphans ai-chat-backend; then
  capture_startup_failure "${release}"
  restore_previous "${previous_release}" "${release}"
  echo "${mode} activation failed; the previous ${mode} release was restored when available." >&2
  exit 1
fi

healthy=false
for _ in $(seq 1 36); do
  if curl --fail --silent "http://127.0.0.1:${management_port}/actuator/health" >/dev/null \
    && curl --silent --output /dev/null --max-time 3 "http://127.0.0.1:${app_port}/"; then
    healthy=true
    break
  fi
  sleep 5
done

if [[ "${healthy}" != true ]]; then
  capture_startup_failure "${release}"
  compose_for "${release}" ps
  restore_previous "${previous_release}" "${release}"
  echo "${mode} health gate failed; Nginx and DNS were not changed." >&2
  exit 1
fi

compose_for "${release}" ps
echo "${mode} is healthy on loopback app port ${app_port} and management port ${management_port}; Nginx and DNS remain unchanged."
