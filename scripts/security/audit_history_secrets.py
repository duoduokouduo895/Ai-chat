#!/usr/bin/env python3
"""Verify that known leaked property values are absent from rewritten Git history.

The script never prints secret values. It extracts literal values from sensitive
keys under a source path, scans every reachable target blob in one cat-file batch,
and reports only counts and matching paths.
"""

from __future__ import annotations

import argparse
import re
import subprocess
from collections import defaultdict
from pathlib import Path


SENSITIVE_KEY = re.compile(
    r"(?i)(password|passwd|secret|api[-_.]?key|access[-_.]?key|private[-_.]?key)$"
)


def git(repo: Path, *args: str, text: bool = True) -> str | bytes:
    result = subprocess.run(
        ["git", "-C", str(repo), *args],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=text,
        encoding="utf-8" if text else None,
        errors="replace" if text else None,
    )
    return result.stdout


def collect_leaked_values(repo: Path, source_path: str) -> dict[bytes, set[str]]:
    objects = str(
        git(repo, "rev-list", "--objects", "--all", "--", source_path)
    ).splitlines()
    property_blobs: dict[str, str] = {}
    for line in objects:
        oid, separator, path = line.partition(" ")
        if separator and path.endswith(".properties"):
            property_blobs[oid] = path

    leaked: dict[bytes, set[str]] = defaultdict(set)
    process = subprocess.Popen(
        ["git", "-C", str(repo), "cat-file", "--batch"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    assert process.stdin is not None
    assert process.stdout is not None
    for oid in property_blobs:
        process.stdin.write(oid.encode("ascii") + b"\n")
        process.stdin.flush()
        header = process.stdout.readline().decode("ascii", errors="replace").strip()
        parts = header.split()
        if len(parts) != 3:
            continue
        _, object_type, size_text = parts
        content = process.stdout.read(int(size_text))
        process.stdout.read(1)
        if object_type != "blob":
            continue
        for raw_line in content.decode("utf-8", errors="replace").splitlines():
            if not raw_line or raw_line.lstrip().startswith("#") or "=" not in raw_line:
                continue
            key, value = (part.strip() for part in raw_line.split("=", 1))
            if not SENSITIVE_KEY.search(key):
                continue
            if len(value) < 6 or "${" in value:
                continue
            leaked[value.encode("utf-8")].add(key)

    process.stdin.close()
    if process.wait() != 0:
        raise RuntimeError("git cat-file failed while collecting leaked values")
    return leaked


def reachable_blobs(repo: Path) -> dict[str, set[str]]:
    paths_by_oid: dict[str, set[str]] = defaultdict(set)
    output = str(git(repo, "rev-list", "--objects", "--all"))
    for line in output.splitlines():
        oid, separator, path = line.partition(" ")
        if separator and path:
            paths_by_oid[oid].add(path)
    return paths_by_oid


def scan_blobs(repo: Path, leaked: dict[bytes, set[str]]) -> dict[str, set[str]]:
    paths_by_oid = reachable_blobs(repo)
    process = subprocess.Popen(
        ["git", "-C", str(repo), "cat-file", "--batch"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    assert process.stdin is not None
    assert process.stdout is not None

    matched_paths: dict[str, set[str]] = defaultdict(set)
    for oid in paths_by_oid:
        process.stdin.write(oid.encode("ascii") + b"\n")
        process.stdin.flush()
        header = process.stdout.readline().decode("ascii", errors="replace").strip()
        parts = header.split()
        if len(parts) != 3:
            continue
        _, object_type, size_text = parts
        size = int(size_text)
        content = process.stdout.read(size)
        process.stdout.read(1)  # trailing newline emitted by --batch
        if object_type != "blob" or b"\x00" in content:
            continue
        for value, keys in leaked.items():
            if value not in content:
                continue
            for path in paths_by_oid[oid]:
                matched_paths[path].update(keys)

    process.stdin.close()
    return_code = process.wait()
    if return_code != 0:
        raise RuntimeError(f"git cat-file exited with {return_code}")
    return matched_paths


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-repo", required=True, type=Path)
    parser.add_argument("--target-repo", required=True, type=Path)
    parser.add_argument("--source-path", default="DEFAULT_GROUP")
    args = parser.parse_args()

    leaked = collect_leaked_values(args.source_repo, args.source_path)
    matched_paths = scan_blobs(args.target_repo, leaked)

    print(f"known_leaked_values={len(leaked)}")
    print(f"matching_paths={len(matched_paths)}")
    for path in sorted(matched_paths):
        keys = ",".join(sorted(matched_paths[path]))
        print(f"match_path={path};keys={keys}")
    return 1 if matched_paths else 0


if __name__ == "__main__":
    raise SystemExit(main())
