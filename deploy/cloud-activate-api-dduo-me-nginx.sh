#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 /opt/ai-chat/releases/<release-name> <migration-id>" >&2
  exit 64
fi
[[ "${AICHAT_API_DDUO_ME_NGINX_CONFIRMED:-}" == ACTIVATE_API_DDUO_ME_NGINX ]] \
  || { echo "Set AICHAT_API_DDUO_ME_NGINX_CONFIRMED=ACTIVATE_API_DDUO_ME_NGINX" >&2; exit 64; }

base_dir="${AICHAT_BASE_DIR:-/opt/ai-chat}"
release="$(readlink -f "$1")"
migration_id="$2"
runtime_env="${base_dir}/runtime/production.env"
template="${release}/deploy/nginx/api.dduo.me.conf.template"
config_dir="${AICHAT_NGINX_CONFIG_DIR:-/www/server/panel/vhost/nginx}"
config_file="${config_dir}/html_api.dduo.me.conf"
cert_file="/www/server/panel/vhost/cert/api.dduo.me/fullchain.pem"
key_file="/www/server/panel/vhost/cert/api.dduo.me/privkey.pem"
evidence_dir="${base_dir}/runtime/domain-migrations/${migration_id}"

[[ "${release}" == "${base_dir}/releases/"* && -d "${release}" && ! -L "${release}" ]] \
  || { echo "Release must be a real directory below ${base_dir}/releases" >&2; exit 65; }
[[ "${migration_id}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$ ]] \
  || { echo "Invalid migration id" >&2; exit 65; }
[[ "$(readlink -f "${base_dir}/current")" == "${release}" ]] \
  || { echo "The approved production release must be active" >&2; exit 66; }
[[ -f "${template}" && ! -L "${template}" && -f "${runtime_env}" && ! -L "${runtime_env}" ]] \
  || { echo "Required template or production runtime config is missing or unsafe" >&2; exit 66; }

env_value() {
  local key="$1" matches=()
  mapfile -t matches < <(grep -E "^${key}=" "${runtime_env}" || true)
  (( ${#matches[@]} == 1 )) || { echo "${key} must occur exactly once" >&2; return 69; }
  printf '%s' "${matches[0]#*=}"
}

[[ "$(env_value AICHAT_PRODUCTION_CONFIRMED)" == true ]] \
  || { echo "Production runtime is not approved" >&2; exit 69; }
app_port="$(env_value AICHAT_APP_PORT)"
management_port="$(env_value AICHAT_MANAGEMENT_PORT)"
[[ "${app_port}" == 18083 && "${management_port}" == 19092 ]] \
  || { echo "Unexpected production ports" >&2; exit 69; }

container="$(docker ps --filter label=com.docker.compose.project=ai-chat-production \
  --filter label=com.docker.compose.service=ai-chat-backend --format '{{.ID}}')"
[[ -n "${container}" \
  && "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "${container}")" == healthy \
  && "$(docker inspect --format '{{.RestartCount}}' "${container}")" == 0 ]] \
  || { echo "Production backend must be healthy with zero restarts" >&2; exit 70; }
curl --fail --silent --max-time 10 "http://127.0.0.1:${management_port}/actuator/health" >/dev/null \
  || { echo "Production management health gate failed" >&2; exit 70; }

command -v nginx >/dev/null && command -v openssl >/dev/null && command -v curl >/dev/null \
  || { echo "nginx, openssl and curl are required" >&2; exit 70; }
[[ -f "${config_file}" && ! -L "${config_file}" && -f "${cert_file}" && ! -L "${cert_file}" \
  && -f "${key_file}" && ! -L "${key_file}" ]] \
  || { echo "BaoTa config or TLS material is missing or unsafe" >&2; exit 68; }
[[ "$(stat -c '%u:%g' "${config_file}")" == 0:0 \
  && "$(stat -c '%u:%g' "${cert_file}")" == 0:0 \
  && "$(stat -c '%u:%g' "${key_file}")" == 0:0 ]] \
  || { echo "BaoTa config or TLS material must be root owned" >&2; exit 68; }
chmod 0600 "${key_file}"
openssl x509 -in "${cert_file}" -noout -checkend 604800 >/dev/null \
  && openssl x509 -in "${cert_file}" -noout -ext subjectAltName | grep -Eq 'DNS:api\.dduo\.me([,[:space:]]|$)' \
  || { echo "TLS certificate must cover api.dduo.me and remain valid for at least seven days" >&2; exit 78; }
cert_key_sha="$(openssl x509 -in "${cert_file}" -pubkey -noout | openssl pkey -pubin -outform DER 2>/dev/null | sha256sum | awk '{print $1}')"
private_key_sha="$(openssl pkey -in "${key_file}" -pubout -outform DER 2>/dev/null | sha256sum | awk '{print $1}')"
[[ -n "${cert_key_sha}" && "${cert_key_sha}" == "${private_key_sha}" ]] \
  || { echo "TLS certificate and private key do not match" >&2; exit 78; }

install -d -o 0 -g 0 -m 0700 "${evidence_dir}"
backup_file="${evidence_dir}/html_api.dduo.me.conf.before"
[[ ! -e "${backup_file}" ]] || { echo "Migration evidence already exists; use a new migration id" >&2; exit 73; }
cp --preserve=mode,ownership,timestamps "${config_file}" "${backup_file}"
chmod 0600 "${backup_file}"

next_config="$(mktemp "${config_dir}/.html_api.dduo.me.conf.next.XXXXXX")"
cleanup() { rm -f -- "${next_config}"; }
trap cleanup EXIT
sed -e "s|__MIGRATION_ID__|${migration_id}|g" \
  -e "s|__APP_PORT__|${app_port}|g" \
  -e "s|__MANAGEMENT_PORT__|${management_port}|g" \
  "${template}" >"${next_config}"
chmod 0644 "${next_config}"

mv "${next_config}" "${config_file}"
trap - EXIT

rollback() {
  cp "${backup_file}" "${config_file}"
  chmod 0644 "${config_file}"
  nginx -t >/dev/null && nginx -s reload || true
}

probe_https_once() {
  curl --noproxy '*' --fail --silent --show-error --connect-timeout 5 --max-time 15 \
    --resolve api.dduo.me:443:127.0.0.1 https://api.dduo.me/healthz >/dev/null
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

if ! nginx -t >/dev/null || ! nginx -s reload; then
  rollback
  echo "Nginx activation failed; BaoTa config was restored" >&2
  exit 75
fi
if ! probe_https; then
  rollback
  echo "Local api.dduo.me health probe failed; BaoTa config was restored" >&2
  exit 75
fi

config_sha="$(sha256sum "${config_file}" | awk '{print $1}')"
cat >"${evidence_dir}/activation.env" <<EOF
MIGRATION_ID=${migration_id}
CONFIG_SHA256=${config_sha}
CERT_SHA256=$(sha256sum "${cert_file}" | awk '{print $1}')
ACTIVATED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF
chmod 0600 "${evidence_dir}/activation.env"

echo "api_dduo_me_nginx_activation=PASS"
echo "migration_id=${migration_id}"
echo "upstream=127.0.0.1:${app_port}"
