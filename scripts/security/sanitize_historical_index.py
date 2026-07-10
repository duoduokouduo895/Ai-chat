#!/usr/bin/env python3
"""Index-filter companion that sanitizes Spring resource secrets without checkout."""

from __future__ import annotations

import re
import subprocess


ASSIGNMENT = re.compile(r"^(\s*)([^#=:\s]+)(\s*[=:]\s*)(.*)$")
SENSITIVE_KEY = re.compile(
    r"(?i)(password|passwd|secret|api[-_.]?key|access[-_.]?key|private[-_.]?key)$"
)


def run(*args: str, data: bytes | None = None) -> bytes:
    return subprocess.run(
        ["git", *args], input=data, stdout=subprocess.PIPE, check=True
    ).stdout


def placeholder(key: str) -> str:
    normalized = re.sub(r"[^A-Za-z0-9]+", "_", key).strip("_").upper()
    return "${" + normalized + ":}"


def sanitize(raw: str) -> str:
    newline = "\r\n" if "\r\n" in raw else "\n"
    trailing_newline = raw.endswith(("\n", "\r"))
    output: list[str] = []
    for line in raw.splitlines():
        match = ASSIGNMENT.match(line)
        if not match:
            output.append(line)
            continue
        indent, key, separator, value = match.groups()
        if not SENSITIVE_KEY.search(key) or "${" in value or not value.strip():
            output.append(line)
            continue
        output.append(f"{indent}{key}{separator}{placeholder(key)}")
    result = newline.join(output)
    return result + newline if trailing_newline else result


def main() -> None:
    entries = run("ls-files", "-s", "--", "src/main/resources")
    for raw_entry in entries.decode("utf-8", errors="replace").splitlines():
        metadata, path = raw_entry.split("\t", 1)
        mode, _old_oid, stage = metadata.split()
        if stage != "0" or not path.endswith((".properties", ".yaml", ".yml")):
            continue
        raw = run("show", f":{path}")
        decoded = raw.decode("utf-8", errors="replace")
        sanitized = sanitize(decoded)
        if sanitized == decoded:
            continue
        new_oid = run("hash-object", "-w", "--stdin", data=sanitized.encode("utf-8")).decode().strip()
        run("update-index", "--cacheinfo", f"{mode},{new_oid},{path}")


if __name__ == "__main__":
    main()
