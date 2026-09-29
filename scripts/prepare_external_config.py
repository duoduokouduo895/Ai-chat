#!/usr/bin/env python3
"""Prepare a reviewable external config from nine ordered Nacos properties exports.

This tool never prints property values. It only writes to a new, restricted output
directory; promoting that directory into runtime is a separate deployment step.
"""

import argparse
import os
import re
from pathlib import Path


SOURCE_FILES = (
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
KEY_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]*\Z")
SENSITIVE_PARTS = re.compile(
    r"password|passphrase|secret|credential|privatekey|apikey|accesskey|auth.?token",
    re.IGNORECASE,
)


def logical_lines(contents: str):
    pending = ""
    continuing = False
    for physical in contents.splitlines():
        if continuing:
            pending += physical.lstrip(" \t\f")
        else:
            pending = physical
        slash_count = len(pending) - len(pending.rstrip("\\"))
        if slash_count % 2:
            pending = pending[:-1]
            continuing = True
            continue
        yield pending
        continuing = False
    if continuing:
        raise ValueError("unfinished properties continuation")


def unescape(value: str) -> str:
    result = []
    index = 0
    special = {"t": "\t", "r": "\r", "n": "\n", "f": "\f"}
    while index < len(value):
        char = value[index]
        if char != "\\":
            result.append(char)
            index += 1
            continue
        index += 1
        if index >= len(value):
            raise ValueError("unfinished properties escape")
        char = value[index]
        if char == "u":
            digits = value[index + 1:index + 5]
            if len(digits) != 4 or not re.fullmatch(r"[0-9A-Fa-f]{4}", digits):
                raise ValueError("invalid unicode escape")
            result.append(chr(int(digits, 16)))
            index += 5
        else:
            result.append(special.get(char, char))
            index += 1
    return "".join(result)


def parse_line(line: str):
    line = line.lstrip(" \t\f")
    if not line or line[0] in "#!":
        return None
    escaped = False
    boundary = len(line)
    for index, char in enumerate(line):
        if not escaped and (char in "=:" or char in " \t\f"):
            boundary = index
            break
        if char == "\\" and not escaped:
            escaped = True
        else:
            escaped = False
    key = unescape(line[:boundary])
    remainder = line[boundary:].lstrip(" \t\f")
    if remainder.startswith(("=", ":")):
        remainder = remainder[1:].lstrip(" \t\f")
    if not KEY_PATTERN.fullmatch(key):
        raise ValueError("unsupported property key; review the export locally")
    if key in {"spring.config.import", "spring.profiles.active"}:
        raise ValueError(f"configuration control key is not allowed: {key}")
    return key, unescape(remainder)


def read_exports(source_dir: Path):
    values = {}
    duplicates = set()
    for name in SOURCE_FILES:
        source = source_dir / name
        if not source.is_file() or source.is_symlink():
            raise ValueError(f"missing or unsafe export file: {name}")
        contents = source.read_bytes().decode("utf-8-sig")
        for line in logical_lines(contents):
            parsed = parse_line(line)
            if parsed is None:
                continue
            key, value = parsed
            if key in values:
                duplicates.add(key)
            values[key] = value
    if not values:
        raise ValueError("the exports contain no properties")
    return values, duplicates


def read_key_list(path: Path):
    keys = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        key = line.strip()
        if key and not key.startswith("#"):
            if not KEY_PATTERN.fullmatch(key):
                raise ValueError("invalid key in key list")
            keys.add(key)
    return keys


def escape_property(value: str) -> str:
    escaped = []
    for index, char in enumerate(value):
        if char == "\\":
            escaped.append("\\\\")
        elif char in "\n\r\t\f":
            escaped.append({"\n": "\\n", "\r": "\\r", "\t": "\\t", "\f": "\\f"}[char])
        elif char == " " and index == 0:
            escaped.append("\\ ")
        elif char in "=:# !":
            escaped.append("\\" + char)
        elif ord(char) > 0x7E or ord(char) < 0x20:
            codepoint = ord(char)
            if codepoint > 0xFFFF:
                codepoint -= 0x10000
                escaped.append(f"\\u{0xD800 + (codepoint >> 10):04x}\\u{0xDC00 + (codepoint & 0x3FF):04x}")
            else:
                escaped.append(f"\\u{codepoint:04x}")
        else:
            escaped.append(char)
    return "".join(escaped)


def write_config(output_dir: Path, values: dict, secrets: set, uid: int | None, gid: int | None):
    if output_dir.exists() or output_dir.is_symlink():
        raise ValueError("output directory already exists; refusing to overwrite it")
    for key in secrets:
        value = values[key]
        if not value or "\x00" in value or "\r" in value or "\n" in value:
            raise ValueError(f"unsupported empty or multiline secret: {key}")
    output_dir.mkdir(mode=0o700, parents=False)
    secret_dir = output_dir / "secrets"
    secret_dir.mkdir(mode=0o700)
    public_file = output_dir / "application.properties"
    with public_file.open("x", encoding="ascii", newline="\n") as handle:
        for key in sorted(values):
            if key not in secrets:
                handle.write(f"{key}={escape_property(values[key])}\n")
    os.chmod(public_file, 0o400)
    for key in sorted(secrets):
        secret_file = secret_dir / key
        with secret_file.open("x", encoding="utf-8", newline="") as handle:
            handle.write(values[key])
        os.chmod(secret_file, 0o400)
    if uid is not None and gid is not None:
        for path in (output_dir, secret_dir, public_file, *(secret_dir / key for key in secrets)):
            os.chown(path, uid, gid)
    os.chmod(secret_dir, 0o500)
    os.chmod(output_dir, 0o500)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_dir", type=Path)
    parser.add_argument("--secret-keys", type=Path, help="reviewed file of exact secret property names")
    parser.add_argument("--public-exceptions", type=Path, help="reviewed nonsecret names matching the sensitive-key heuristic")
    parser.add_argument("--output-dir", type=Path, help="new staging directory; must not already exist")
    parser.add_argument("--uid", type=int)
    parser.add_argument("--gid", type=int)
    args = parser.parse_args()
    if (args.uid is None) != (args.gid is None):
        parser.error("--uid and --gid must be provided together")
    if (args.output_dir is None) != (args.secret_keys is None):
        parser.error("--output-dir and --secret-keys must be provided together")
    try:
        values, duplicates = read_exports(args.source_dir)
        suspected = {key for key in values if SENSITIVE_PARTS.search(key)}
        print(f"properties={len(values)} duplicates={len(duplicates)}")
        for key in sorted(values):
            print(f"key: {key}")
        for key in sorted(duplicates):
            print(f"duplicate: {key}")
        for key in sorted(suspected):
            print(f"review-secret: {key}")
        if args.output_dir is None:
            return
        secrets = read_key_list(args.secret_keys)
        exceptions = read_key_list(args.public_exceptions) if args.public_exceptions else set()
        if unknown := (secrets | exceptions) - values.keys():
            raise ValueError("key list names are absent from exports: " + ", ".join(sorted(unknown)))
        if missing := suspected - secrets - exceptions:
            raise ValueError("sensitive-looking keys need classification: " + ", ".join(sorted(missing)))
        if overlap := secrets & exceptions:
            raise ValueError("keys cannot be both secret and public: " + ", ".join(sorted(overlap)))
        write_config(args.output_dir, values, secrets, args.uid, args.gid)
        print(f"prepared public={len(values) - len(secrets)} secret={len(secrets)}")
    except (OSError, UnicodeError, ValueError) as error:
        parser.exit(2, f"prepare failed: {error}\n")


if __name__ == "__main__":
    main()
