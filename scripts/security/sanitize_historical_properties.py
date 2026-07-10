#!/usr/bin/env python3
"""Replace literal secrets in historical Spring resource files with placeholders.

Designed for use from a Git history rewrite tree-filter. Existing environment or
Nacos placeholders are preserved. The script never logs original values.
"""

from __future__ import annotations

import re
from pathlib import Path


ASSIGNMENT = re.compile(r"^(\s*)([^#=:\s]+)(\s*[=:]\s*)(.*)$")
SENSITIVE_KEY = re.compile(
    r"(?i)(password|passwd|secret|api[-_.]?key|access[-_.]?key|private[-_.]?key)$"
)


def placeholder(key: str) -> str:
    normalized = re.sub(r"[^A-Za-z0-9]+", "_", key).strip("_").upper()
    return "${" + normalized + ":}"


def sanitize(path: Path) -> None:
    raw = path.read_text(encoding="utf-8", errors="replace")
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

    rendered = newline.join(output)
    if trailing_newline:
        rendered += newline
    if rendered != raw:
        path.write_text(rendered, encoding="utf-8", newline="")


def main() -> None:
    resources = Path("src/main/resources")
    if not resources.exists():
        return
    for path in resources.rglob("*"):
        if path.is_file() and path.suffix.lower() in {".properties", ".yaml", ".yml"}:
            sanitize(path)


if __name__ == "__main__":
    main()
