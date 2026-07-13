#!/bin/sh
set -eu

load_secret() {
  variable_name="$1"
  secret_file="$2"

  if [ ! -s "${secret_file}" ]; then
    echo "Required secret file is missing or empty: ${secret_file}" >&2
    exit 78
  fi

  secret_value="$(cat "${secret_file}")"
  if [ -z "${secret_value}" ]; then
    echo "Required secret file is empty: ${secret_file}" >&2
    exit 78
  fi

  export "${variable_name}=${secret_value}"
  unset secret_value
}

case "${AICHAT_REQUIRE_NACOS_AUTH:-false}" in
  true)
    load_secret NACOS_USERNAME /run/secrets/nacos.username
    load_secret NACOS_PASSWORD /run/secrets/nacos.password
    ;;
  false)
    ;;
  *)
    echo "AICHAT_REQUIRE_NACOS_AUTH must be true or false" >&2
    exit 78
    ;;
esac

exec java -jar /opt/aichat/application.jar
