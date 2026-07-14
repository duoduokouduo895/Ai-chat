#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <cutover-id>" >&2
  exit 64
fi
[[ "${AICHAT_PRODUCTION_NGINX_ROLLBACK_CONFIRMED:-}" == ROLLBACK_AI_CHAT_PRODUCTION_NGINX \
  && "${AICHAT_PUBLIC_DNS_ROLLBACK_CONFIRMED:-}" == DNS_POINTS_AWAY_FROM_GCP ]] \
  || { echo "Set both Nginx and public DNS rollback confirmation values" >&2; exit 64; }

cutover_id="$1"
base_dir="${AICHAT_BASE_DIR:-/opt/ai-chat}"
runtime_dir="${base_dir}/runtime"
nginx_bin="${AICHAT_NGINX_BIN:-nginx}"
config_dir="${AICHAT_NGINX_CONFIG_DIR:-/www/server/panel/vhost/nginx}"
config_file="${config_dir}/api.dduo.chat.conf"
evidence_dir="${runtime_dir}/cutovers/${cutover_id}/nginx"
evidence_file="${evidence_dir}/activation.env"
lock_file="${runtime_dir}/.production-nginx.lock"
gcp_ip="${AICHAT_GCP_PUBLIC_IP:-34.150.80.173}"

[[ "${cutover_id}" =~ ^[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}$ ]] \
  || { echo "Invalid cutover id" >&2; exit 65; }
[[ -f "${config_file}" && ! -L "${config_file}" \
  && "$(grep -E '^# cutover-id: ' "${config_file}")" == "# cutover-id: ${cutover_id}" \
  && -f "${evidence_file}" && ! -L "${evidence_file}" ]] \
  || { echo "Managed Nginx config or activation evidence is missing" >&2; exit 69; }
config_sha="$(sha256sum "${config_file}" | awk '{print $1}')"
grep -qxF "CONFIG_SHA256=${config_sha}" "${evidence_file}" \
  || { echo "Nginx config digest does not match activation evidence" >&2; exit 78; }
if getent ahostsv4 api.dduo.chat | awk '{print $1}' | grep -qxF "${gcp_ip}"; then
  echo "Public DNS still resolves to GCP; keep Nginx serving until DNS rollback propagates" >&2
  exit 70
fi
[[ -f "${lock_file}" && ! -L "${lock_file}" && "$(stat -c '%a:%u:%g' "${lock_file}")" == '600:0:0' ]] \
  || { echo "Production Nginx lock is missing or unsafe" >&2; exit 68; }
exec 9<>"${lock_file}"
flock -n 9 || { echo "Another production Nginx operation is running" >&2; exit 75; }

archive_dir="${evidence_dir}/disabled-$(date -u +%Y%m%dT%H%M%SZ)"
install -d -o 0 -g 0 -m 0700 "${archive_dir}"
mv "${config_file}" "${archive_dir}/api.dduo.chat.conf"
chmod 0600 "${archive_dir}/api.dduo.chat.conf"
if ! "${nginx_bin}" -t >/dev/null || ! "${nginx_bin}" -s reload; then
  mv "${archive_dir}/api.dduo.chat.conf" "${config_file}"
  chmod 0644 "${config_file}"
  "${nginx_bin}" -t >/dev/null && "${nginx_bin}" -s reload || true
  echo "Nginx rollback validation/reload failed; managed config was restored" >&2
  exit 75
fi

cat >"${archive_dir}/rollback.env" <<EOF
CUTOVER_ID=${cutover_id}
ROLLED_BACK_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
DNS_GCP_IP=${gcp_ip}
EOF
chmod 0600 "${archive_dir}/rollback.env"
echo "production_nginx_rollback=PASS"
echo "cutover_id=${cutover_id}"
echo "archive=${archive_dir}"
