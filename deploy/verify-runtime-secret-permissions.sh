#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <secret-directory> <secret-name> [<secret-name> ...]" >&2
  exit 64
fi

secret_dir="$1"
shift
for secret_name in "$@"; do
  [[ "${secret_name}" =~ ^[a-zA-Z0-9._-]+$ ]] \
    || { echo "Unsafe secret file name: ${secret_name}" >&2; exit 68; }
  secret_file="${secret_dir}/${secret_name}"
  [[ -f "${secret_file}" && ! -L "${secret_file}" && -s "${secret_file}" ]] \
    || { echo "Runtime secret must be a non-empty regular file: ${secret_file}" >&2; exit 69; }

  read -r mode uid gid < <(stat -c '%a %u %g' "${secret_file}")
  [[ "${mode}" == 400 && "${uid}" == 10001 && "${gid}" == 10001 ]] \
    || {
      echo "Runtime secret must use mode 400 and uid/gid 10001:10001: ${secret_file}" >&2
      exit 69
    }
done
