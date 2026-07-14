#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 /opt/ai-chat/releases/<release-name> <cutover-id>" >&2
  exit 64
fi
[[ "${AICHAT_PRODUCTION_NGINX_CONFIRMED:-}" == ACTIVATE_AI_CHAT_PRODUCTION_NGINX ]] \
  || { echo "Set AICHAT_PRODUCTION_NGINX_CONFIRMED=ACTIVATE_AI_CHAT_PRODUCTION_NGINX" >&2; exit 64; }

base_dir="${AICHAT_BASE_DIR:-/opt/ai-chat}"
release="$(readlink -f "$1")"
cutover_id="$2"
runtime_dir="${base_dir}/runtime"
runtime_env="${runtime_dir}/production.env"
template="${release}/deploy/nginx/api.dduo.chat.conf.template"
nginx_bin="${AICHAT_NGINX_BIN:-nginx}"
config_dir="${AICHAT_NGINX_CONFIG_DIR:-/www/server/panel/vhost/nginx}"
config_file="${config_dir}/api.dduo.chat.conf"
cert_file="${AICHAT_TLS_CERT_FILE:-/www/server/panel/vhost/cert/api.dduo.chat/fullchain.pem}"
key_file="${AICHAT_TLS_KEY_FILE:-/www/server/panel/vhost/cert/api.dduo.chat/privkey.pem}"
evidence_dir="${runtime_dir}/cutovers/${cutover_id}/nginx"
evidence_file="${evidence_dir}/activation.env"
lock_file="${runtime_dir}/.production-nginx.lock"

[[ "${release}" == "${base_dir}/releases/"* && -d "${release}" && ! -L "${release}" ]] \
  || { echo "Release must be a real directory below ${base_dir}/releases" >&2; exit 65; }
[[ "${cutover_id}" =~ ^[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}$ ]] \
  || { echo "Invalid cutover id" >&2; exit 65; }
[[ "$(readlink -f "${base_dir}/current")" == "${release}" ]] \
  || { echo "The approved AI-chat production release must be active" >&2; exit 66; }
[[ -f "${template}" && ! -L "${template}" ]] \
  || { echo "Nginx template is missing or unsafe" >&2; exit 66; }
[[ -f "${runtime_env}" && ! -L "${runtime_env}" \
  && "$(stat -c '%a:%u:%g' "${runtime_env}")" == '600:0:0' ]] \
  || { echo "Production runtime config is missing or unsafe" >&2; exit 67; }

env_value() {
  local key="$1"
  local matches=()
  mapfile -t matches < <(grep -E "^${key}=" "${runtime_env}" || true)
  (( ${#matches[@]} == 1 )) || { echo "${key} must occur exactly once" >&2; return 69; }
  printf '%s' "${matches[0]#*=}"
}
[[ "$(env_value AICHAT_CUTOVER_ID)" == "${cutover_id}" \
  && "$(env_value AICHAT_PRODUCTION_CONFIRMED)" == true \
  && "$(env_value AICHAT_SERVICE_NAME)" == ai-chat-production ]] \
  || { echo "Production runtime config is not approved for this cutover" >&2; exit 69; }
app_port="$(env_value AICHAT_APP_PORT)"
management_port="$(env_value AICHAT_MANAGEMENT_PORT)"
[[ "${app_port}" =~ ^[0-9]+$ && "${app_port}" == 18083 ]] \
  || { echo "Production Nginx upstream must use loopback port 18083" >&2; exit 69; }
[[ "${management_port}" =~ ^[0-9]+$ && "${management_port}" == 19092 ]] \
  || { echo "Production health upstream must use loopback port 19092" >&2; exit 69; }

mapfile -t production_containers < <(docker ps \
  --filter label=com.docker.compose.project=ai-chat-production \
  --filter label=com.docker.compose.service=ai-chat-backend --format '{{.ID}}')
[[ ${#production_containers[@]} -eq 1 \
  && "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "${production_containers[0]}")" == healthy \
  && "$(docker inspect --format '{{.RestartCount}}' "${production_containers[0]}")" == 0 ]] \
  || { echo "AI-chat production must be healthy with zero restarts" >&2; exit 70; }
for port in "${app_port}" "${management_port}"; do
  listeners="$(ss -ltnH "sport = :${port}")"
  grep -Eq "127\\.0\\.0\\.1:${port}([[:space:]]|$)" <<<"${listeners}" \
    && ! grep -Eq "(0\\.0\\.0\\.0|\\[::\\]):${port}([[:space:]]|$)" <<<"${listeners}" \
    || { echo "Production port ${port} must listen on IPv4 loopback only" >&2; exit 70; }
done

command -v "${nginx_bin}" >/dev/null && command -v openssl >/dev/null && command -v curl >/dev/null \
  || { echo "nginx, openssl and curl are required" >&2; exit 70; }
for path in "${config_dir}" "${cert_file}" "${key_file}"; do
  [[ "${path}" =~ ^/[a-zA-Z0-9._/-]+$ ]] || { echo "Unsafe Nginx/TLS path: ${path}" >&2; exit 68; }
done
[[ -d "${config_dir}" && ! -L "${config_dir}" && "$(stat -c '%u' "${config_dir}")" == 0 \
  && -z "$(find "${config_dir}" -maxdepth 0 -perm /022 -print -quit)" \
  && -f "$(readlink -f "${cert_file}")" && -f "$(readlink -f "${key_file}")" \
  && "$(stat -c '%u' "$(readlink -f "${cert_file}")")" == 0 \
  && "$(stat -c '%u' "$(readlink -f "${key_file}")")" == 0 \
  && -z "$(find "$(readlink -f "${key_file}")" -perm /077 -print -quit)" ]] \
  || { echo "Nginx config directory or TLS material is unsafe" >&2; exit 68; }
openssl x509 -in "${cert_file}" -noout -checkend 604800 >/dev/null \
  && openssl x509 -in "${cert_file}" -noout -ext subjectAltName | grep -Eq 'DNS:api\.dduo\.chat([,[:space:]]|$)' \
  || { echo "TLS certificate must cover api.dduo.chat and remain valid for at least seven days" >&2; exit 78; }
cert_key_sha="$(openssl x509 -in "${cert_file}" -pubkey -noout | openssl pkey -pubin -outform DER 2>/dev/null | sha256sum | awk '{print $1}')"
private_key_sha="$(openssl pkey -in "${key_file}" -pubout -outform DER 2>/dev/null | sha256sum | awk '{print $1}')"
[[ -n "${cert_key_sha}" && "${cert_key_sha}" == "${private_key_sha}" ]] \
  || { echo "TLS certificate and private key do not match" >&2; exit 78; }

if [[ -e "${lock_file}" ]]; then
  [[ -f "${lock_file}" && ! -L "${lock_file}" && "$(stat -c '%a:%u:%g' "${lock_file}")" == '600:0:0' ]] \
    || { echo "Production Nginx lock is unsafe" >&2; exit 68; }
else
  install -o 0 -g 0 -m 0600 /dev/null "${lock_file}"
fi
exec 9<>"${lock_file}"
flock -n 9 || { echo "Another production Nginx operation is running" >&2; exit 75; }
install -d -o 0 -g 0 -m 0700 "${evidence_dir}"

probe_https_once() {
  local health_status protected_api_status
  health_status="$(curl --silent --show-error --connect-timeout 5 --max-time 15 \
    --resolve api.dduo.chat:443:127.0.0.1 --output /dev/null --write-out '%{http_code}' \
    https://api.dduo.chat/healthz)"
  protected_api_status="$(curl --silent --show-error --connect-timeout 5 --max-time 15 \
    --resolve api.dduo.chat:443:127.0.0.1 --output /dev/null --write-out '%{http_code}' \
    https://api.dduo.chat/api/ai/models)"
  [[ "${health_status}" == 200 && "${protected_api_status}" == 401 ]]
}

probe_https() {
  local deadline=$((SECONDS + 20))
  while (( SECONDS < deadline )); do
    if probe_https_once; then
      return 0
    fi
    sleep 0.25
  done
  return 1
}

if [[ -e "${config_file}" ]]; then
  [[ -f "${config_file}" && ! -L "${config_file}" \
    && "$(grep -E '^# cutover-id: ' "${config_file}")" == "# cutover-id: ${cutover_id}" \
    && -f "${evidence_file}" && ! -L "${evidence_file}" ]] \
    || { echo "Refusing to overwrite an unmanaged or different-cutover Nginx config" >&2; exit 73; }
  config_sha="$(sha256sum "${config_file}" | awk '{print $1}')"
  grep -qxF "CONFIG_SHA256=${config_sha}" "${evidence_file}" \
    || { echo "Existing Nginx config digest does not match its evidence" >&2; exit 78; }
  "${nginx_bin}" -t >/dev/null && probe_https \
    || { echo "Existing managed Nginx route is not healthy" >&2; exit 75; }
  echo "production_nginx_activation=PASS"
  echo "cutover_id=${cutover_id}"
  echo "mutation=idempotent-none"
  exit 0
fi
[[ ! -e "${evidence_file}" ]] \
  || { echo "Activation evidence already exists without an active config; use a new cutover id" >&2; exit 73; }

next_config="$(mktemp "${config_dir}/.api.dduo.chat.conf.next.XXXXXX")"
cleanup() { rm -f -- "${next_config}"; }
trap cleanup EXIT
sed -e "s|__CUTOVER_ID__|${cutover_id}|g" \
  -e "s|__CERT_FILE__|${cert_file}|g" \
  -e "s|__KEY_FILE__|${key_file}|g" \
  -e "s|__APP_PORT__|${app_port}|g" \
  -e "s|__MANAGEMENT_PORT__|${management_port}|g" \
  "${template}" >"${next_config}"
chmod 0644 "${next_config}"
mv "${next_config}" "${config_file}"
trap - EXIT

rollback_failed_activation() {
  local reason="$1"
  local failed_config="${evidence_dir}/failed-api.dduo.chat-$(date -u +%Y%m%dT%H%M%SZ).conf"
  mv "${config_file}" "${failed_config}"
  chmod 0600 "${failed_config}"
  "${nginx_bin}" -t >/dev/null && "${nginx_bin}" -s reload || true
  echo "${reason}" >&2
  exit 75
}
"${nginx_bin}" -t >/dev/null || rollback_failed_activation "Nginx config validation failed; new config was withdrawn"
"${nginx_bin}" -s reload || rollback_failed_activation "Nginx reload failed; new config was withdrawn"
probe_https || rollback_failed_activation "Local TLS/upstream probe failed; new config was withdrawn"

config_sha="$(sha256sum "${config_file}" | awk '{print $1}')"
cert_sha="$(sha256sum "${cert_file}" | awk '{print $1}')"
cat >"${evidence_file}" <<EOF
CUTOVER_ID=${cutover_id}
CONFIG_SHA256=${config_sha}
CERT_SHA256=${cert_sha}
ACTIVATED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF
chmod 0600 "${evidence_file}"
cp "${config_file}" "${evidence_dir}/api.dduo.chat.conf"
chmod 0600 "${evidence_dir}/api.dduo.chat.conf"

echo "production_nginx_activation=PASS"
echo "cutover_id=${cutover_id}"
echo "upstream=127.0.0.1:${app_port}"
echo "dns_mutation=none"
