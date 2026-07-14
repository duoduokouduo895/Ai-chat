#!/usr/bin/env bash
set -Eeuo pipefail

[[ "$(id -u)" -eq 0 ]] || { echo "Run as root" >&2; exit 77; }
[[ $# -eq 1 ]] || { echo "Usage: $0 /path/to/cloud-control-legacy-writer.sh" >&2; exit 64; }

control="$(readlink -f "$1")"
[[ -f "${control}" ]] || { echo "Control script is missing" >&2; exit 66; }
command -v systemd-run >/dev/null || { echo "systemd-run is required" >&2; exit 69; }

fixture="$(mktemp -d /tmp/aichat-legacy-lifecycle.XXXXXX)"
unit_base="aichat-legacy-mock-$RANDOM-$RANDOM"
service_name="${unit_base}.service"
base_dir="${fixture}/bundles"
drift_cutover="test-drift-$RANDOM"
success_cutover="test-success-$RANDOM"
drift_recovery="aichat-legacy-recovery-$(systemd-escape "${drift_cutover}").service"
success_recovery="aichat-legacy-recovery-$(systemd-escape "${success_cutover}").service"
app_port=""
for candidate_port in $(seq 28080 28180); do
  if ! ss -Hltn | awk '{print $4}' | grep -Eq "(^|:|\\])${candidate_port}$"; then
    app_port="${candidate_port}"
    break
  fi
done
[[ -n "${app_port}" ]] || { echo "No free test application port was found" >&2; exit 69; }

cleanup() {
  systemctl stop "${success_recovery}" "${drift_recovery}" "${service_name}" >/dev/null 2>&1 || true
  systemctl reset-failed "${success_recovery}" "${drift_recovery}" "${service_name}" >/dev/null 2>&1 || true
  rm -rf -- "${fixture}"
}
trap cleanup EXIT

cat >"${fixture}/fake-java.py" <<'PY'
#!/usr/bin/env python3
import signal
import socket
import os
import sys
import time

running = True

def stop(_signum, _frame):
    global running
    running = False

signal.signal(signal.SIGTERM, stop)
server = socket.socket()
server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server.bind(("127.0.0.1", int(os.environ["MOCK_APP_PORT"])))
server.listen(1)
server.settimeout(0.2)
while running:
    try:
        connection, _ = server.accept()
        connection.close()
    except TimeoutError:
        pass
server.close()
PY
chmod 0700 "${fixture}/fake-java.py"
printf 'mock-legacy-jar\n' >"${fixture}/fake.jar"
chmod 0600 "${fixture}/fake.jar"

wait_for_port() {
  local expected="$1" deadline=$((SECONDS + 20))
  while (( SECONDS < deadline )); do
    if ss -Hltn | awk '{print $4}' | grep -Eq "(^|:|\\])${app_port}$"; then
      [[ "${expected}" == up ]] && return 0
    else
      [[ "${expected}" == down ]] && return 0
    fi
    sleep 1
  done
  echo "Timed out waiting for test port ${app_port} to become ${expected}" >&2
  return 1
}

wait_for_unit_removal() {
  local deadline=$((SECONDS + 20))
  while [[ "$(systemctl show --property=LoadState --value "${service_name}" 2>/dev/null || true)" != not-found ]] \
      && (( SECONDS < deadline )); do
    sleep 1
  done
  [[ "$(systemctl show --property=LoadState --value "${service_name}" 2>/dev/null || true)" == not-found ]]
}

start_mock_service() {
  systemd-run --unit="${unit_base}" --service-type=simple --collect \
    --property="WorkingDirectory=${fixture}" --property=User=root \
    --setenv="MOCK_APP_PORT=${app_port}" \
    /usr/bin/python3 "${fixture}/fake-java.py" -jar "${fixture}/fake.jar" >/dev/null
  wait_for_port up
  systemctl is-active --quiet "${service_name}"
}

stop_mock_service() {
  systemctl stop "${service_name}" >/dev/null 2>&1 || true
  wait_for_port down
  systemctl reset-failed "${service_name}" >/dev/null 2>&1 || true
  wait_for_unit_removal
}

run_control() {
  AICHAT_LEGACY_SERVICE="${service_name}" \
  AICHAT_LEGACY_CUTOVER_BASE="${base_dir}" \
  AICHAT_LEGACY_APP_PORT="${app_port}" \
    bash "${control}" "$@"
}

expect_exit() {
  local expected="$1"
  shift
  set +e
  "$@" >/dev/null 2>&1
  local actual=$?
  set -e
  [[ "${actual}" -eq "${expected}" ]] \
    || { echo "Expected exit ${expected}, got ${actual}: $*" >&2; exit 1; }
}

# A prepared bundle must be bound to the exact MainPID. A service restart invalidates it.
start_mock_service
first_pid="$(systemctl show --property=MainPID --value "${service_name}")"
run_control prepare "${drift_cutover}" >/dev/null
stop_mock_service
start_mock_service
second_pid="$(systemctl show --property=MainPID --value "${service_name}")"
[[ "${first_pid}" != "${second_pid}" ]] || { echo "Mock MainPID did not change" >&2; exit 1; }
expect_exit 69 env \
  AICHAT_LEGACY_SERVICE="${service_name}" \
  AICHAT_LEGACY_CUTOVER_BASE="${base_dir}" \
  AICHAT_LEGACY_APP_PORT="${app_port}" \
  AICHAT_LEGACY_STOP_CONFIRMED=STOP_LEGACY_WRITER \
  bash "${control}" stop "${drift_cutover}"
stop_mock_service

# The success path proves confirmation gates, graceful stop evidence and guarded recovery.
start_mock_service
run_control prepare "${success_cutover}" >/dev/null
expect_exit 64 run_control stop "${success_cutover}"
AICHAT_LEGACY_STOP_CONFIRMED=STOP_LEGACY_WRITER \
  run_control stop "${success_cutover}" >/dev/null
wait_for_port down
grep -qxF 'OLD_PRODUCTION_WRITERS_STOPPED=true' "${base_dir}/${success_cutover}/stopped.env"
grep -qxF 'LEGACY_MAIN_PID_RUNNING=false' "${base_dir}/${success_cutover}/stopped.env"
grep -qxF "LEGACY_APP_PORT=${app_port}" "${base_dir}/${success_cutover}/stopped.env"
grep -qxF 'LEGACY_APP_PORT_LISTENING=false' "${base_dir}/${success_cutover}/stopped.env"
expect_exit 64 env \
  AICHAT_LEGACY_SERVICE="${service_name}" \
  AICHAT_LEGACY_CUTOVER_BASE="${base_dir}" \
  AICHAT_LEGACY_APP_PORT="${app_port}" \
  AICHAT_LEGACY_START_CONFIRMED=START_LEGACY_WRITER \
  bash "${control}" start "${success_cutover}"
AICHAT_LEGACY_START_CONFIRMED=START_LEGACY_WRITER \
AICHAT_GCP_PRODUCTION_STOPPED_CONFIRMED=GCP_PRODUCTION_IS_STOPPED \
  run_control start "${success_cutover}" >/dev/null
wait_for_port up
systemctl is-active --quiet "${success_recovery}"
grep -qxF 'LEGACY_WRITER_RESTORED=true' "${base_dir}/${success_cutover}/restored.env"

echo "legacy_writer_lifecycle_test=PASS"
echo "pid_drift_rejected=true"
echo "unconfirmed_stop_rejected=true"
echo "unconfirmed_restore_rejected=true"
echo "graceful_stop_and_restore=true"
echo "isolated_test_port=${app_port}"
