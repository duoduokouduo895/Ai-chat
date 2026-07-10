#!/usr/bin/env python3
"""Verify Nacos configuration availability without printing sensitive values."""

from __future__ import annotations

import argparse
import re
import urllib.parse
import urllib.request
from pathlib import Path

from audit_history_secrets import collect_leaked_values


DATA_IDS = (
    "databaseR2DBC-config.properties",
    "redis-config.properties",
    "langchain4j-config.properties",
    "cache-config.properties",
    "rabbitmq-config.properties",
    "api-rate-limit-config.properties",
    "chat-elasticsearch-config.properties",
    "knowledge-base-config.properties",
    "jwt-config.properties",
)
SENSITIVE_KEY = re.compile(
    r"(?i)(password|passwd|secret|api[-_.]?key|access[-_.]?key|private[-_.]?key)$"
)


def fetch(server: str, data_id: str, group: str, namespace: str) -> str:
    query = {"dataId": data_id, "group": group}
    if namespace:
        query["tenant"] = namespace
    url = f"http://{server}/nacos/v1/cs/configs?{urllib.parse.urlencode(query)}"
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(url, timeout=10) as response:
        return response.read().decode("utf-8", errors="replace")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--server", required=True)
    parser.add_argument("--group", default="DEFAULT_GROUP")
    parser.add_argument("--namespace", default="")
    parser.add_argument("--repo", required=True, type=Path)
    args = parser.parse_args()

    historical = collect_leaked_values(args.repo, "DEFAULT_GROUP")
    historical_values = set(historical)
    print("anonymous_read_check=true")

    for data_id in DATA_IDS:
        try:
            content = fetch(args.server, data_id, args.group, args.namespace)
        except Exception as error:
            print(f"config={data_id};status=unavailable;error={type(error).__name__}")
            continue

        sensitive_keys: list[str] = []
        historical_matches: list[str] = []
        for line in content.splitlines():
            if not line or line.lstrip().startswith("#") or "=" not in line:
                continue
            key, value = (part.strip() for part in line.split("=", 1))
            if not SENSITIVE_KEY.search(key):
                continue
            sensitive_keys.append(key)
            if value.encode("utf-8") in historical_values:
                historical_matches.append(key)

        keys = ",".join(sensitive_keys) if sensitive_keys else "none"
        matches = ",".join(historical_matches) if historical_matches else "none"
        print(f"config={data_id};status=available;sensitive_keys={keys};matches_git_history={matches}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
