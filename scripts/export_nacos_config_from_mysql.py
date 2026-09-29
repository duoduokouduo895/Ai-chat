#!/usr/bin/env python3
"""Export the active AI-chat Nacos properties inside the server, without value logs.

Run as root on the home server. The MySQL bootstrap password remains inside the
MySQL container; no property content is printed to stdout or stderr.
"""

import argparse
import os
import re
import subprocess
from pathlib import Path

from prepare_external_config import SOURCE_FILES


ENV_PATH = Path("/opt/ai-chat-home/runtime/home-ai-chat.env")
MYSQL_SHELL = """set -e
MYSQL_PWD="$(cat /run/secrets/mysql_bootstrap_root_password)"
export MYSQL_PWD
exec mysql -N -B --default-character-set=utf8mb4 -uroot -D nacos_v3_candidate -e "$1"
"""
SAFE_SQL_VALUE = re.compile(r"[A-Za-z0-9_.-]*\Z")


def runtime_value(name: str) -> str:
    found = None
    with ENV_PATH.open(encoding="utf-8") as handle:
        for line in handle:
            if line.startswith(name + "="):
                if found is not None:
                    raise ValueError(f"duplicate runtime setting: {name}")
                found = line.split("=", 1)[1].strip()
    if found is None:
        raise ValueError(f"missing runtime setting: {name}")
    if len(found) >= 2 and found[0] in "\"'" and found[-1] == found[0]:
        found = found[1:-1]
    if not SAFE_SQL_VALUE.fullmatch(found):
        raise ValueError(f"unsupported runtime setting syntax: {name}")
    return found


def mysql_hex(query: str) -> bytes:
    result = subprocess.run(
        ["docker", "exec", "mysql", "sh", "-c", MYSQL_SHELL, "sh", query],
        capture_output=True,
        check=False,
    )
    if result.returncode:
        raise RuntimeError("MySQL metadata or export query failed")
    return result.stdout.strip()


def exported_contents(data_id: str, group: str, namespace: str) -> bytes:
    query = ("SELECT HEX(content) FROM config_info WHERE "
             f"data_id='{data_id}' AND group_id='{group}' AND tenant_id='{namespace}'")
    result = mysql_hex(query)
    if not result or b"\n" in result or not re.fullmatch(rb"[0-9A-Fa-f]+", result):
        raise ValueError(f"expected exactly one nonempty config row: {data_id}")
    return bytes.fromhex(result.decode("ascii"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, help="new root-only directory; omit for inventory check")
    args = parser.parse_args()
    try:
        if os.geteuid() != 0:
            raise ValueError("run as root on the home server")
        group = runtime_value("NACOS_GROUP")
        namespace = runtime_value("NACOS_NAMESPACE")
        exports = {name: exported_contents(name, group, namespace) for name in SOURCE_FILES}
        print(f"active_group_exports={len(exports)}")
        for name in SOURCE_FILES:
            print(f"found: {name}")
        if args.output_dir is None:
            return
        if args.output_dir.exists() or args.output_dir.is_symlink():
            raise ValueError("output directory already exists; refusing to overwrite it")
        args.output_dir.mkdir(mode=0o700, parents=False)
        for name, contents in exports.items():
            path = args.output_dir / name
            descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(descriptor, "wb") as handle:
                handle.write(contents)
                handle.flush()
                os.fsync(handle.fileno())
        print(f"exported_files={len(exports)}")
    except (OSError, ValueError, RuntimeError) as error:
        parser.exit(2, f"export failed: {error}\n")


if __name__ == "__main__":
    main()
