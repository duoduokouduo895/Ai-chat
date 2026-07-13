#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <cutover-id>" >&2
  exit 64
fi

cutover_id="$1"
[[ "${cutover_id}" =~ ^[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}$ ]] \
  || { echo "Invalid cutover id" >&2; exit 64; }

base_dir="${AICHAT_BASE_DIR:-/opt/ai-chat}"
runtime_env="${base_dir}/runtime/production.env"
cutover_dir="${base_dir}/runtime/cutovers/${cutover_id}"
mysql_container="${AICHAT_MYSQL_CONTAINER:-mysql}"
schema="ai_agent_db"

[[ -f "${runtime_env}" && ! -L "${runtime_env}" ]] \
  || { echo "Production runtime config is missing or unsafe" >&2; exit 67; }
grep -qxF "AICHAT_CUTOVER_ID=${cutover_id}" "${runtime_env}" \
  || { echo "Cutover id does not match production.env" >&2; exit 69; }
grep -qxF 'AICHAT_PRODUCTION_CONFIRMED=false' "${runtime_env}" \
  || { echo "Database backup must be taken before production confirmation" >&2; exit 69; }
docker inspect "${mysql_container}" >/dev/null 2>&1 \
  || { echo "MySQL container is missing" >&2; exit 70; }

container_env_value() {
  local key="$1"
  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "${mysql_container}" \
    | awk -v key="${key}" 'index($0, key "=") == 1 {sub("^[^=]*=", ""); print; exit}'
}

mysql_root_password="$(container_env_value MYSQL_ROOT_PASSWORD)"
if [[ -z "${mysql_root_password}" ]]; then
  mysql_root_password_file="$(container_env_value MYSQL_ROOT_PASSWORD_FILE)"
  [[ "${mysql_root_password_file}" == /* ]] \
    || { echo "Unable to locate MySQL root credentials" >&2; exit 70; }
  mysql_root_password="$(docker exec "${mysql_container}" sh -c 'cat "$1"' _ "${mysql_root_password_file}")"
fi
[[ -n "${mysql_root_password}" ]] \
  || { echo "MySQL root credentials are empty" >&2; exit 70; }

mysql_root() {
  docker exec -e MYSQL_PWD="${mysql_root_password}" "${mysql_container}" \
    mysql --protocol=socket -uroot --batch --skip-column-names "$@"
}

[[ "$(mysql_root -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${schema}'")" == 1 ]] \
  || { echo "Expected application schema is missing: ${schema}" >&2; exit 70; }
flyway_state="$(mysql_root -e "SELECT CONCAT(COALESCE(MAX(version),'missing'),'|',MIN(success)) FROM ${schema}.flyway_schema_history")"
[[ "${flyway_state}" == '4|1' ]] \
  || { echo "Expected successful Flyway version 4 before V1 cutover, got ${flyway_state}" >&2; exit 78; }

available_disk_mib="$(df -Pm "${base_dir}" | awk 'NR == 2 {print $4}')"
(( available_disk_mib >= 4096 )) \
  || { echo "At least 4096 MiB free disk is required for the database backup" >&2; exit 70; }

umask 077
install -d -o 0 -g 0 -m 0700 "${base_dir}/runtime/cutovers" "${cutover_dir}"
backup_file="${cutover_dir}/${schema}.sql.gz"
checksum_file="${backup_file}.sha256"
[[ ! -e "${backup_file}" && ! -e "${checksum_file}" ]] \
  || { echo "Cutover backup already exists; use a new cutover id" >&2; exit 73; }

temporary_backup="$(mktemp "${cutover_dir}/.${schema}.XXXXXX.sql.gz")"
cleanup() {
  rm -f -- "${temporary_backup}"
  unset mysql_root_password
}
trap cleanup EXIT

docker exec -e MYSQL_PWD="${mysql_root_password}" "${mysql_container}" \
  mysqldump --protocol=socket -uroot --single-transaction --quick \
    --routines --events --triggers --hex-blob --no-tablespaces \
    --default-character-set=utf8mb4 "${schema}" \
  | gzip -9 >"${temporary_backup}"
gzip --test "${temporary_backup}"
[[ -s "${temporary_backup}" ]] || { echo "Database backup is empty" >&2; exit 74; }
chmod 0600 "${temporary_backup}"
mv -f "${temporary_backup}" "${backup_file}"
(cd "${cutover_dir}" && sha256sum "$(basename "${backup_file}")" >"$(basename "${checksum_file}")")
chmod 0600 "${backup_file}" "${checksum_file}"

cat >"${cutover_dir}/database-backup.env" <<EOF
CUTOVER_ID=${cutover_id}
DATABASE_SCHEMA=${schema}
FLYWAY_VERSION_BEFORE_CUTOVER=4
BACKUP_CREATED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF
chmod 0600 "${cutover_dir}/database-backup.env"

echo "database_backup=created-and-verified"
echo "cutover_id=${cutover_id}"
echo "schema=${schema}"
echo "flyway_version_before_cutover=4"
