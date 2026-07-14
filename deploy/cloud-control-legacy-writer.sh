#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat >&2 <<'EOF'
Usage:
  cloud-control-legacy-writer.sh prepare <cutover-id>
  cloud-control-legacy-writer.sh stop <cutover-id>
  cloud-control-legacy-writer.sh start <cutover-id>

prepare captures a root-only recovery bundle without stopping the legacy JVM.
stop requires AICHAT_LEGACY_STOP_CONFIRMED=STOP_LEGACY_WRITER.
start requires both AICHAT_LEGACY_START_CONFIRMED=START_LEGACY_WRITER and
AICHAT_GCP_PRODUCTION_STOPPED_CONFIRMED=GCP_PRODUCTION_IS_STOPPED.
EOF
  exit 64
}

[[ $# -eq 2 ]] || usage
action="$1"
cutover_id="$2"
[[ "${action}" =~ ^(prepare|stop|start)$ ]] || usage
[[ "${cutover_id}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$ ]] \
  || { echo "Invalid cutover id" >&2; exit 64; }
[[ "$(id -u)" -eq 0 ]] || { echo "Run as root" >&2; exit 77; }

service_name="${AICHAT_LEGACY_SERVICE:-spring_dduo-chat.service}"
base_dir="${AICHAT_LEGACY_CUTOVER_BASE:-/opt/ai-chat-legacy-cutover}"
app_port="${AICHAT_LEGACY_APP_PORT:-8080}"
[[ "${app_port}" =~ ^[0-9]+$ && "${app_port}" -ge 1 && "${app_port}" -le 65535 ]] \
  || { echo "Invalid legacy application port" >&2; exit 64; }
bundle_dir="${base_dir}/${cutover_id}"
lock_file="${base_dir}/.legacy-writer.lock"

install -d -m 0700 -o root -g root "${base_dir}"
touch "${lock_file}"
chown root:root "${lock_file}"
chmod 0600 "${lock_file}"
exec 9>"${lock_file}"
flock -n 9 || { echo "Another legacy writer operation is running" >&2; exit 75; }

read_marker_value() {
  local file="$1" key="$2" matches=()
  mapfile -t matches < <(grep -E "^${key}=" "${file}" || true)
  (( ${#matches[@]} == 1 )) || { echo "${key} must occur exactly once in ${file}" >&2; return 69; }
  printf '%s' "${matches[0]#*=}"
}

current_main_pid() {
  systemctl show --property=MainPID --value "${service_name}"
}

app_port_is_listening() {
  ss -Hltn 2>/dev/null | awk '{print $4}' | grep -Eq "(^|:|\\])${app_port}$"
}

write_launcher() {
  local target="$1"
  cat >"${target}" <<'PY'
#!/usr/bin/env python3
import os
import pathlib
import sys

bundle = pathlib.Path(sys.argv[1]).resolve()
argv = [item for item in (bundle / "command.bin").read_bytes().split(b"\0") if item]
environment = {}
for item in (bundle / "environment.bin").read_bytes().split(b"\0"):
    if not item:
        continue
    key, separator, value = item.partition(b"=")
    if not separator or not key:
        raise SystemExit("invalid captured environment")
    environment[key] = value

original_jar = (bundle / "original-jar.path").read_bytes().strip()
backup_jar = os.fsencode(bundle / "legacy-application.jar")
argv = [backup_jar if item == original_jar else item for item in argv]
if not argv or not os.path.isfile(argv[0]):
    raise SystemExit("captured Java executable is missing")
os.execve(argv[0], argv, environment)
PY
  chmod 0700 "${target}"
}

prepare_bundle() {
  [[ ! -e "${bundle_dir}" ]] \
    || { echo "Recovery bundle already exists; use a new cutover id" >&2; exit 73; }
  systemctl is-active --quiet "${service_name}" \
    || { echo "Legacy service is not active" >&2; exit 69; }

  local pid command_file jar_path working_dir temporary_dir jar_sha backup_sha
  pid="$(current_main_pid)"
  [[ "${pid}" =~ ^[1-9][0-9]*$ && -r "/proc/${pid}/cmdline" && -r "/proc/${pid}/environ" ]] \
    || { echo "Legacy service MainPID is unavailable" >&2; exit 69; }
  command_file="/proc/${pid}/cmdline"
  mapfile -d '' -t command <"${command_file}"
  jar_path=""
  for ((index=0; index < ${#command[@]} - 1; index++)); do
    if [[ "${command[index]}" == -jar ]]; then
      jar_path="${command[index + 1]}"
      break
    fi
  done
  [[ -n "${jar_path}" && -f "${jar_path}" && ! -L "${jar_path}" ]] \
    || { echo "Unable to identify a safe legacy application Jar" >&2; exit 66; }
  [[ -x "${command[0]}" ]] || { echo "Captured Java executable is not executable" >&2; exit 66; }
  command -v python3 >/dev/null || { echo "python3 is required for exact recovery" >&2; exit 69; }
  working_dir="$(readlink -f "/proc/${pid}/cwd")"
  [[ -d "${working_dir}" ]] || { echo "Legacy working directory is missing" >&2; exit 66; }

  umask 077
  temporary_dir="$(mktemp -d "${base_dir}/.${cutover_id}.next.XXXXXX")"
  cleanup_prepare() { rm -rf -- "${temporary_dir}"; }
  trap cleanup_prepare RETURN
  cp --reflink=auto --preserve=mode,timestamps "${jar_path}" "${temporary_dir}/legacy-application.jar"
  cp "${command_file}" "${temporary_dir}/command.bin"
  cp "/proc/${pid}/environ" "${temporary_dir}/environment.bin"
  printf '%s\n' "${jar_path}" >"${temporary_dir}/original-jar.path"
  printf '%s\n' "${working_dir}" >"${temporary_dir}/working-directory.path"
  write_launcher "${temporary_dir}/launch-captured.py"
  python3 - "${temporary_dir}/launch-captured.py" <<'PY'
import pathlib
import sys
compile(pathlib.Path(sys.argv[1]).read_text(), sys.argv[1], "exec")
PY

  jar_sha="$(sha256sum "${jar_path}" | awk '{print $1}')"
  backup_sha="$(sha256sum "${temporary_dir}/legacy-application.jar" | awk '{print $1}')"
  [[ "${jar_sha}" == "${backup_sha}" ]] || { echo "Legacy Jar backup checksum mismatch" >&2; exit 74; }
  cat >"${temporary_dir}/prepared.env" <<EOF
CUTOVER_ID=${cutover_id}
LEGACY_SERVICE=${service_name}
LEGACY_MAIN_PID=${pid}
LEGACY_APP_PORT=${app_port}
LEGACY_JAR_SHA256=${jar_sha}
RECOVERY_BUNDLE_SHA256=${backup_sha}
PREPARED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF
  chown -R root:root "${temporary_dir}"
  find "${temporary_dir}" -type d -exec chmod 0700 {} +
  find "${temporary_dir}" -type f -exec chmod 0600 {} +
  chmod 0700 "${temporary_dir}/launch-captured.py"
  mv "${temporary_dir}" "${bundle_dir}"
  trap - RETURN

  echo "legacy_recovery_prepare=PASS"
  echo "cutover_id=${cutover_id}"
  echo "legacy_main_pid=${pid}"
  echo "legacy_jar_sha256=${jar_sha}"
  echo "service_mutation=none"
}

stop_writer() {
  [[ "${AICHAT_LEGACY_STOP_CONFIRMED:-}" == STOP_LEGACY_WRITER ]] \
    || { echo "Explicit legacy stop confirmation is required" >&2; exit 64; }
  local marker="${bundle_dir}/prepared.env" pid expected_pid expected_sha actual_sha deadline
  [[ -f "${marker}" && ! -L "${marker}" && "$(stat -c '%a:%u:%g' "${marker}")" == 600:0:0 ]] \
    || { echo "A verified root-only recovery bundle is required before stop" >&2; exit 70; }
  [[ ! -e "${bundle_dir}/stopped.env" ]] \
    || { echo "Legacy writer stop evidence already exists" >&2; exit 73; }
  expected_pid="$(read_marker_value "${marker}" LEGACY_MAIN_PID)"
  [[ "$(read_marker_value "${marker}" LEGACY_APP_PORT)" == "${app_port}" ]] \
    || { echo "Legacy application port changed after recovery preparation" >&2; exit 69; }
  pid="$(current_main_pid)"
  [[ "${pid}" == "${expected_pid}" && -d "/proc/${pid}" ]] \
    || { echo "Legacy MainPID changed after recovery preparation" >&2; exit 69; }
  expected_sha="$(read_marker_value "${marker}" LEGACY_JAR_SHA256)"
  actual_sha="$(sha256sum "${bundle_dir}/legacy-application.jar" | awk '{print $1}')"
  [[ "${actual_sha}" == "${expected_sha}" ]] \
    || { echo "Recovery Jar checksum mismatch" >&2; exit 74; }

  systemctl kill --kill-who=main --signal=TERM "${service_name}"
  deadline=$((SECONDS + 45))
  while kill -0 "${pid}" 2>/dev/null && (( SECONDS < deadline )); do sleep 1; done
  if kill -0 "${pid}" 2>/dev/null; then
    echo "Legacy JVM did not stop after SIGTERM; no SIGKILL was issued" >&2
    exit 75
  fi
  if app_port_is_listening; then
    echo "Legacy application port is still listening after JVM stop" >&2
    exit 75
  fi
  if ss -Htpn 2>/dev/null | grep -E ':(3306|5672|6379|8848|9200|9848|9849)[[:space:]]' \
      | grep -q 'java'; then
    echo "A Java process still has middleware connections; writer stop is not proven" >&2
    exit 75
  fi
  umask 077
  cat >"${bundle_dir}/stopped.env" <<EOF
CUTOVER_ID=${cutover_id}
OLD_PRODUCTION_WRITERS_STOPPED=true
LEGACY_MAIN_PID_RUNNING=false
LEGACY_APP_PORT=${app_port}
LEGACY_APP_PORT_LISTENING=false
LEGACY_JAR_SHA256=${expected_sha}
STOPPED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF
  chown root:root "${bundle_dir}/stopped.env"
  chmod 0600 "${bundle_dir}/stopped.env"
  echo "legacy_writer_stop=PASS"
  echo "cutover_id=${cutover_id}"
  echo "legacy_main_pid_running=false"
  echo "legacy_app_port=${app_port}"
  echo "legacy_app_port_listening=false"
}

start_writer() {
  [[ "${AICHAT_LEGACY_START_CONFIRMED:-}" == START_LEGACY_WRITER ]] \
    || { echo "Explicit legacy start confirmation is required" >&2; exit 64; }
  [[ "${AICHAT_GCP_PRODUCTION_STOPPED_CONFIRMED:-}" == GCP_PRODUCTION_IS_STOPPED ]] \
    || { echo "Confirm GCP production is stopped before restoring the legacy writer" >&2; exit 64; }
  local marker="${bundle_dir}/prepared.env" stop_evidence="${bundle_dir}/stopped.env"
  local expected_sha actual_sha working_dir unit deadline pid
  [[ -f "${marker}" && -f "${stop_evidence}" ]] \
    || { echo "Prepared and stopped evidence are required for legacy recovery" >&2; exit 70; }
  [[ ! -e "${bundle_dir}/restored.env" ]] \
    || { echo "Legacy recovery was already recorded" >&2; exit 73; }
  [[ "$(current_main_pid)" == 0 ]] \
    || { echo "The original legacy service still has a MainPID" >&2; exit 69; }
  [[ "$(read_marker_value "${marker}" LEGACY_APP_PORT)" == "${app_port}" ]] \
    || { echo "Legacy application port changed after recovery preparation" >&2; exit 69; }
  ! app_port_is_listening || { echo "Legacy application port is already in use" >&2; exit 69; }
  expected_sha="$(read_marker_value "${marker}" LEGACY_JAR_SHA256)"
  actual_sha="$(sha256sum "${bundle_dir}/legacy-application.jar" | awk '{print $1}')"
  [[ "${actual_sha}" == "${expected_sha}" ]] \
    || { echo "Recovery Jar checksum mismatch" >&2; exit 74; }
  working_dir="$(cat "${bundle_dir}/working-directory.path")"
  [[ -d "${working_dir}" ]] || { echo "Legacy working directory is missing" >&2; exit 66; }
  unit="aichat-legacy-recovery-$(systemd-escape "${cutover_id}")"
  ! systemctl is-active --quiet "${unit}.service" \
    || { echo "Legacy recovery unit is already active" >&2; exit 73; }

  systemd-run --unit="${unit}" --service-type=simple --collect \
    --property="WorkingDirectory=${working_dir}" --property=User=root \
    /usr/bin/python3 "${bundle_dir}/launch-captured.py" "${bundle_dir}" >/dev/null
  deadline=$((SECONDS + 90))
  while (( SECONDS < deadline )); do
    if systemctl is-active --quiet "${unit}.service" && app_port_is_listening; then break; fi
    if systemctl is-failed --quiet "${unit}.service"; then
      journalctl -u "${unit}.service" -n 40 --no-pager >&2 || true
      echo "Legacy recovery unit failed" >&2
      exit 75
    fi
    sleep 2
  done
  systemctl is-active --quiet "${unit}.service" && app_port_is_listening \
    || { echo "Legacy recovery did not become ready on its captured application port" >&2; exit 75; }
  pid="$(systemctl show --property=MainPID --value "${unit}.service")"
  umask 077
  cat >"${bundle_dir}/restored.env" <<EOF
CUTOVER_ID=${cutover_id}
LEGACY_WRITER_RESTORED=true
RECOVERY_UNIT=${unit}.service
RECOVERY_MAIN_PID=${pid}
LEGACY_JAR_SHA256=${expected_sha}
RESTORED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF
  chown root:root "${bundle_dir}/restored.env"
  chmod 0600 "${bundle_dir}/restored.env"
  echo "legacy_writer_restore=PASS"
  echo "cutover_id=${cutover_id}"
  echo "recovery_unit=${unit}.service"
  echo "recovery_main_pid=${pid}"
}

case "${action}" in
  prepare) prepare_bundle ;;
  stop) stop_writer ;;
  start) start_writer ;;
esac
