#!/usr/bin/env python3
"""Long-conversation memory evaluation: plant facts/constraints, push them out with filler turns, then probe.

Each scenario runs in its own conversation (non-RAG chat). Filler turns make the history exceed the model
input budget, so a correct probe answer must come from the rolling summary, a kept constraint or archive recall.
Run once with ASSISTANT_MEMORY_ENABLED=true and once with false on the backend to compare.
"""

from __future__ import annotations

import json
import os
import socket
import time
import urllib.error
import urllib.request
from collections import defaultdict
from pathlib import Path

from common import (RESULTS_DIR, base_url, latest_manifest, no_proxy_opener, percentile, run_evidence, timestamp,
                    write_csv, write_pointer)

DATASET = Path(__file__).resolve().parents[1] / "dataset" / "memory_conversations.jsonl"
FILLER_TOPICS = [
    "详细解释 TCP 三次握手和四次挥手，每一步都举例说明。",
    "详细比较 B+ 树和 LSM 树的读写放大，给出适用场景。",
    "详细介绍 JVM 的 G1 垃圾回收器各阶段和调优参数。",
    "详细说明 OAuth 2.0 授权码流程以及 PKCE 的作用。",
    "详细讲解 Linux 的 cgroup v2 内存控制和 OOM 行为。",
    "详细对比 REST 与 gRPC 在超时、重试和流控上的差异。",
    "详细解释 MySQL 的 MVCC 和各隔离级别下的可见性。",
    "详细介绍一致性哈希和虚拟节点在缓存集群中的应用。",
    "详细说明 Kubernetes 滚动更新中 readiness 探针的作用。",
    "详细讲解 CAP 与 PACELC，并各举两个真实系统的例子。",
    "详细介绍 HTTP 缓存头 Cache-Control、ETag 的协作方式。",
    "详细说明布隆过滤器的误判率计算与参数选择。",
]


def load_scenarios() -> list[dict]:
    with DATASET.open(encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def score(answer: str, scenario: dict) -> dict:
    lowered = answer.lower()
    expected = scenario["expected_keywords"]
    hits = [word for word in expected if word.lower() in lowered]
    leaked = [word for word in scenario["forbidden_keywords"] if word.lower() in lowered]
    return {
        "keywordCoverage": round(len(hits) / len(expected), 4) if expected else 1.0,
        "forbiddenHits": leaked,
        "pass": len(hits) == len(expected) and not leaked,
    }


def parse_sse(lines) -> dict:
    """Collects message chunks, the first error and the conversation id from the metadata event."""
    event, chunks, error, conversation_id = None, [], "", None
    for line in lines:
        if line.startswith("event:"):
            event = line[6:].strip()
        elif line.startswith("data:"):
            data = line[5:].lstrip()
            if data == "[DONE]":
                break
            if event == "error":
                error = error or data
            elif event == "metadata":
                conversation_id = json.loads(data).get("conversationId")
            elif event == "message" and data:
                chunks.append(data)
        elif not line:
            event = None
    return {"conversationId": conversation_id, "answer": "".join(chunks), "error": error}


def stream(token: str, body: dict, timeout: int) -> dict:
    request = urllib.request.Request(
        f"{base_url()}/api/ai/chat",
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": f"Bearer {token}", "Accept": "text/event-stream",
                 "Content-Type": "application/json; charset=utf-8"},
        method="POST")
    started = time.perf_counter()
    first = []

    def lines(response):
        event = None
        for raw in response:
            line = raw.decode("utf-8", errors="replace").rstrip("\r\n")
            if line.startswith("event:"):
                event = line[6:].strip()
            elif not line:
                event = None
            elif event == "message" and not first and line[5:].strip():
                first.append(round((time.perf_counter() - started) * 1000, 2))
            yield line

    try:
        with no_proxy_opener().open(request, timeout=timeout) as response:
            result = parse_sse(lines(response))
    except (urllib.error.URLError, socket.timeout, TimeoutError) as error:
        result = {"conversationId": body.get("conversationId"), "answer": "", "error": f"超时或断流: {error}"}
    result["conversationId"] = result["conversationId"] or body.get("conversationId")
    result["ttftMs"] = first[0] if first else None
    result["totalMs"] = round((time.perf_counter() - started) * 1000, 2)
    result["success"] = not result["error"] and bool(result["answer"])
    if not result["error"] and not result["answer"]:
        result["error"] = "SSE 正常结束，但未收到 message 事件"
    return result


def main() -> None:
    manifest = latest_manifest()
    token = os.getenv("MEMORY_EVAL_TOKEN") or manifest["userToken"]
    model = os.getenv("RAG_EVAL_MODEL", "chat-fast")
    timeout = int(os.getenv("RAG_EVAL_SSE_TIMEOUT_SECONDS", "120"))
    fillers = int(os.getenv("MEMORY_EVAL_FILLER_TURNS", "10"))
    settle = int(os.getenv("MEMORY_EVAL_SETTLE_SECONDS", "20"))
    label = os.getenv("MEMORY_EVAL_LABEL", "memory-on")
    run_id = timestamp()
    rows = []
    for scenario in load_scenarios():
        conversation_id = None
        failed = ""
        prompts = scenario["setup"] + [FILLER_TOPICS[i % len(FILLER_TOPICS)] for i in range(fillers)]
        for index, prompt in enumerate(prompts):
            print(f"记忆评测: {scenario['id']} 铺垫 {index + 1}/{len(prompts)}", flush=True)
            result = stream(token, request_body(prompt, model, conversation_id, f"mem-{run_id}-{scenario['id']}-{index}"),
                            timeout)
            conversation_id = conversation_id or result["conversationId"]
            if not result["success"]:
                failed = f"setup turn {index + 1}: {result['error']}"
                break
        if not failed:
            time.sleep(settle)  # compaction runs in the background after the turn completes
        probe = {"success": False, "answer": "", "ttftMs": None, "totalMs": None, "error": failed} if failed else \
            stream(token, request_body(scenario["probe"], model, conversation_id, f"mem-{run_id}-{scenario['id']}-probe"),
                   timeout)
        rows.append({"scenarioId": scenario["id"], "category": scenario["category"], "label": label,
                     "conversationId": conversation_id, "success": probe["success"], "ttftMs": probe["ttftMs"],
                     "totalMs": probe["totalMs"], **score(probe["answer"], scenario), "answer": probe["answer"],
                     "error": probe["error"]})

    by_category = defaultdict(list)
    for row in rows:
        by_category[row["category"]].append(row)
    summary = {
        "label": label,
        "scenarios": len(rows),
        "passRate": round(sum(row["pass"] for row in rows) / len(rows), 4) if rows else 0,
        "byCategory": {name: round(sum(row["pass"] for row in items) / len(items), 4)
                       for name, items in sorted(by_category.items())},
        "forbiddenLeaks": [row["scenarioId"] for row in rows if row["forbiddenHits"]],
        "probeTtftP50Ms": percentile((row["ttftMs"] for row in rows if row["ttftMs"] is not None), 0.50),
        "probeTotalP95Ms": percentile((row["totalMs"] for row in rows if row["totalMs"] is not None), 0.95),
    }
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    output = RESULTS_DIR / f"memory-{label}-{run_id}.json"
    output.write_text(json.dumps({"runId": run_id, "model": model, "summary": summary, "rows": rows,
                                  "evidence": run_evidence({"model": model, "fillers": fillers, "settle": settle})},
                                 ensure_ascii=False, indent=2), encoding="utf-8")
    write_csv(RESULTS_DIR / f"memory-{label}-{run_id}.csv", rows)
    write_pointer("latest-memory.txt", output)
    print(json.dumps(summary, ensure_ascii=False, indent=2), flush=True)


def request_body(prompt: str, model: str, conversation_id, client_id: str) -> dict:
    body = {"prompt": prompt, "model": model, "clientMessageId": client_id, "saveMessage": True, "streaming": True}
    if conversation_id is not None:
        body["conversationId"] = conversation_id
    return body


if __name__ == "__main__":
    main()
