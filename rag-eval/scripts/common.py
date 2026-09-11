#!/usr/bin/env python3
"""Shared helpers for the local RAG evaluation scripts."""

from __future__ import annotations

import csv
import hashlib
import json
import math
import os
import statistics
import subprocess
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable

ROOT = Path(__file__).resolve().parents[1]
LOCAL_DIR = ROOT / "local"
RESULTS_DIR = ROOT / "results"
DATASET_PATH = ROOT / "dataset" / "questions.jsonl"


def base_url() -> str:
    return os.getenv("RAG_EVAL_BASE_URL", "http://127.0.0.1:8080").rstrip("/")


def no_proxy_opener() -> urllib.request.OpenerDirector:
    return urllib.request.build_opener(urllib.request.ProxyHandler({}))


def json_request(path: str, token: str | None = None, method: str = "GET",
                 body: dict[str, Any] | None = None, timeout: int = 30) -> Any:
    headers = {"Accept": "application/json"}
    payload = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(f"{base_url()}{path}", data=payload, headers=headers, method=method)
    try:
        with no_proxy_opener().open(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {error.code} {error.reason}: {detail}") from error


def read_questions() -> list[dict[str, Any]]:
    with DATASET_PATH.open(encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def latest_manifest() -> dict[str, Any]:
    pointer = LOCAL_DIR / "latest-manifest.txt"
    if not pointer.exists():
        raise RuntimeError("未找到 rag-eval/local/latest-manifest.txt，请先运行 setup.ps1。")
    path = Path(pointer.read_text(encoding="utf-8").strip())
    if not path.exists():
        raise RuntimeError(f"manifest 不存在: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def write_pointer(name: str, path: Path) -> None:
    LOCAL_DIR.mkdir(parents=True, exist_ok=True)
    (LOCAL_DIR / name).write_text(str(path.resolve()), encoding="utf-8")


def timestamp() -> str:
    return datetime.now().strftime("%Y%m%d_%H%M%S")


def percentile(values: Iterable[float], ratio: float) -> float:
    sorted_values = sorted(values)
    if not sorted_values:
        return 0.0
    index = max(0, math.ceil(len(sorted_values) * ratio) - 1)
    return round(float(sorted_values[index]), 2)


def mean(values: Iterable[float]) -> float:
    materialized = list(values)
    return round(statistics.fmean(materialized), 4) if materialized else 0.0


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    if not rows:
        path.write_text("", encoding="utf-8")
        return
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def marker_hits(matches: list[dict[str, Any]], required: list[str]) -> list[str]:
    contents = "\n".join(str(match.get("content", "")) for match in matches)
    return [marker for marker in required if marker in contents]


def is_refusal(answer: str) -> bool:
    text = answer.lower()
    phrases = ("无法确认", "信息不足", "没有相关", "未检索到", "无法回答", "资料不足", "不能确定")
    return any(phrase in text for phrase in phrases)


def keyword_coverage(answer: str, keywords: list[str]) -> float:
    if not keywords:
        return 1.0
    text = answer.lower()
    matched = sum(1 for keyword in keywords if keyword.lower() in text)
    return round(matched / len(keywords), 4)


def run_evidence(config: Any) -> dict[str, Any]:
    """Record reproducibility fingerprints, never configuration secrets or diffs."""
    repository = ROOT.parent
    def git(*args: str) -> bytes:
        return subprocess.check_output(["git", *args], cwd=repository, stderr=subprocess.DEVNULL)
    corpus = hashlib.sha256()
    for path in sorted((ROOT / "dataset").rglob("*")):
        if path.is_file() and "__pycache__" not in path.parts:
            corpus.update(path.relative_to(ROOT).as_posix().encode())
            corpus.update(path.read_bytes())
    try:
        revision = git("rev-parse", "HEAD").decode().strip()
        changes = hashlib.sha256(git("diff", "HEAD", "--no-ext-diff", "--binary"))
        for name in git("ls-files", "--others", "--exclude-standard", "-z").split(b"\0"):
            if name:
                path = repository / os.fsdecode(name)
                if path.is_file():
                    changes.update(name)
                    changes.update(path.read_bytes())
        change_digest = changes.hexdigest()
    except (OSError, subprocess.CalledProcessError):
        revision, change_digest = "UNAVAILABLE", "UNAVAILABLE"
    return {"metricVersion": 2, "revision": revision, "worktreeSha256": change_digest,
            "datasetSha256": corpus.hexdigest(),
            "configSha256": hashlib.sha256(json.dumps(config, sort_keys=True).encode()).hexdigest(),
            "generationAssessment": "keyword/refusal heuristics; human review required"}
