#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 || "$2" != --confirm-old-writers-stopped ]]; then
  echo "Usage: $0 /opt/ai-chat/releases/<release-name> --confirm-old-writers-stopped" >&2
  exit 64
fi

base_dir="${AICHAT_BASE_DIR:-/opt/ai-chat}"
release="$(readlink -f "$1")"
runtime_env="${base_dir}/runtime/production.env"
[[ -n "${release}" && "${release}" == "${base_dir}/releases/"* ]] \
  || { echo "Release must exist below ${base_dir}/releases" >&2; exit 65; }
[[ -f "${runtime_env}" && ! -L "${runtime_env}" ]] \
  || { echo "Production runtime config is missing or unsafe" >&2; exit 67; }

env_value() {
  local key="$1"
  local matches=()
  mapfile -t matches < <(grep -E "^${key}=" "${runtime_env}" || true)
  (( ${#matches[@]} == 1 )) || { echo "${key} must occur exactly once" >&2; return 69; }
  printf '%s' "${matches[0]#*=}"
}

cutover_id="$(env_value AICHAT_CUTOVER_ID)"
[[ "${cutover_id}" =~ ^[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}$ ]] \
  || { echo "Invalid AICHAT_CUTOVER_ID" >&2; exit 69; }
[[ "$(env_value AICHAT_PRODUCTION_CONFIRMED)" == false ]] \
  || { echo "Approval evidence must be recorded before enabling production" >&2; exit 69; }

"${release}/deploy/cloud-preflight-production.sh" --prepare "${release}" >/dev/null

cutover_dir="${base_dir}/runtime/cutovers/${cutover_id}"
backup_file="${cutover_dir}/ai_agent_db.sql.gz"
checksum_file="${backup_file}.sha256"
legacy_stop_evidence="${cutover_dir}/legacy-old-writer-stopped.env"
[[ -f "${backup_file}" && -f "${checksum_file}" ]] \
  || { echo "Verified ai_agent_db backup is required before approval" >&2; exit 70; }
(cd "${cutover_dir}" && sha256sum --check "$(basename "${checksum_file}")" >/dev/null)
gzip --test "${backup_file}"
[[ -f "${legacy_stop_evidence}" && ! -L "${legacy_stop_evidence}" \
  && "$(stat -c '%a:%u:%g' "${legacy_stop_evidence}")" == 600:0:0 ]] \
  || { echo "Root-only legacy writer stop evidence is required before approval" >&2; exit 70; }
for expected in \
  "CUTOVER_ID=${cutover_id}" \
  'OLD_PRODUCTION_WRITERS_STOPPED=true' \
  'LEGACY_MAIN_PID_RUNNING=false' \
  'LEGACY_APP_PORT=8080' \
  'LEGACY_APP_PORT_LISTENING=false'; do
  [[ "$(grep -cFx "${expected}" "${legacy_stop_evidence}")" -eq 1 ]] \
    || { echo "Legacy writer stop evidence is incomplete or belongs to another cutover" >&2; exit 70; }
done

writer_evidence="${cutover_dir}/old-writers-stopped.env"
approval_evidence="${cutover_dir}/approved.env"
[[ ! -e "${writer_evidence}" && ! -e "${approval_evidence}" ]] \
  || { echo "Cutover approval already exists; use a new cutover id instead of overwriting evidence" >&2; exit 73; }

release_manifest_sha256="$({
  find "${release}" -type f ! -path '*/.git/*' -print0 \
    | sort -z \
    | xargs -0 sha256sum
} | sha256sum | awk '{print $1}')"
recorded_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
umask 077
cat >"${writer_evidence}" <<EOF
CUTOVER_ID=${cutover_id}
OLD_PRODUCTION_WRITERS_STOPPED=true
LEGACY_STOP_EVIDENCE_SHA256=$(sha256sum "${legacy_stop_evidence}" | awk '{print $1}')
RECORDED_AT=${recorded_at}
EOF
cat >"${approval_evidence}" <<EOF
CUTOVER_ID=${cutover_id}
RELEASE_MANIFEST_SHA256=${release_manifest_sha256}
PRODUCTION_ACTIVATION_APPROVED=true
RECORDED_AT=${recorded_at}
EOF
chown 0:0 "${writer_evidence}" "${approval_evidence}"
chmod 0600 "${writer_evidence}" "${approval_evidence}"

echo "production_cutover_approval=recorded"
echo "cutover_id=${cutover_id}"
echo "release_manifest_sha256=${release_manifest_sha256}"
echo "next=stop-local-candidate-then-enable-production-and-run-ready-preflight"
