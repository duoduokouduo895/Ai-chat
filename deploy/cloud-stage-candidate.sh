#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /opt/ai-chat/releases/<release-name>" >&2
  exit 64
fi

release="$(readlink -f "$1")"
base_dir="${AICHAT_BASE_DIR:-/opt/ai-chat}"
runtime_dir="${base_dir}/runtime"
candidate_link="${base_dir}/candidate"
next_link="${base_dir}/.candidate.next"
runtime_env="${runtime_dir}/candidate.env"

[[ "${release}" == "${base_dir}/releases/"* ]] || { echo "Release must be below ${base_dir}/releases" >&2; exit 65; }
[[ -f "${release}/compose.candidate.yaml" && -f "${release}/versions.env" ]] \
  || { echo "Release is incomplete" >&2; exit 66; }
[[ -f "${runtime_env}" ]] || { echo "Candidate runtime config is missing" >&2; exit 67; }
[[ "$(stat -c '%a' "${runtime_env}")" =~ ^(600|640)$ ]] \
  || { echo "Candidate runtime config must use mode 600 or 640" >&2; exit 68; }
docker network inspect middleware-net >/dev/null
docker network inspect nacos-net >/dev/null
docker network inspect aidiag-telemetry >/dev/null

candidate_port="$(sed -n 's/^AICHAT_CANDIDATE_PORT=//p' "${runtime_env}" | tail -n 1 | tr -d '\r')"
[[ "${candidate_port}" =~ ^[0-9]+$ ]] || { echo "AICHAT_CANDIDATE_PORT must be numeric" >&2; exit 69; }

compose_for() {
  local target_release="$1"
  docker compose --project-name ai-chat-candidate --project-directory "${target_release}" \
    -f "${target_release}/compose.candidate.yaml" \
    --env-file "${target_release}/versions.env" --env-file "${runtime_env}" "${@:2}"
}

compose_for "${release}" config --quiet
compose_for "${release}" build ai-chat-backend

previous_release=""
if [[ -L "${candidate_link}" ]]; then
  previous_release="$(readlink -f "${candidate_link}")"
fi

ln -s "${release}" "${next_link}"
mv -Tf "${next_link}" "${candidate_link}"

if ! compose_for "${candidate_link}" up -d --remove-orphans ai-chat-backend; then
  if [[ -n "${previous_release}" ]]; then
    ln -s "${previous_release}" "${next_link}"
    mv -Tf "${next_link}" "${candidate_link}"
    compose_for "${candidate_link}" up -d --remove-orphans ai-chat-backend || true
  fi
  echo "Candidate activation failed; previous candidate was restored when available." >&2
  exit 1
fi

healthy=false
for _ in $(seq 1 36); do
  if curl --fail --silent "http://127.0.0.1:${candidate_port}/actuator/health" >/dev/null; then
    healthy=true
    break
  fi
  sleep 5
done

if [[ "${healthy}" != true ]]; then
  compose_for "${candidate_link}" ps
  if [[ -n "${previous_release}" ]]; then
    ln -s "${previous_release}" "${next_link}"
    mv -Tf "${next_link}" "${candidate_link}"
    compose_for "${candidate_link}" up -d --remove-orphans ai-chat-backend || true
  else
    compose_for "${candidate_link}" down --remove-orphans || true
    rm -f "${candidate_link}"
  fi
  echo "Candidate health gate failed; DNS and Nginx were not changed." >&2
  exit 1
fi

compose_for "${candidate_link}" ps
echo "Candidate is healthy on loopback port ${candidate_port}; DNS and Nginx remain unchanged."
