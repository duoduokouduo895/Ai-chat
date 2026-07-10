#!/usr/bin/env python3
"""Run sampled RAG generation evaluation against the unified SSE chat endpoint."""

from __future__ import annotations

import json
import os
import socket
import time
import urllib.error
import urllib.request
from collections import defaultdict

from common import RESULTS_DIR, base_url, is_refusal, keyword_coverage, latest_manifest, no_proxy_opener, percentile, read_questions, timestamp, write_csv, write_pointer


def stream_answer(token, body, timeout):
    request = urllib.request.Request(
        f"{base_url()}/api/ai/chat",
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "text/event-stream",
            "Content-Type": "application/json; charset=utf-8",
        },
        method="POST",
    )
    started = time.perf_counter()
    ttft_ms = None
    chunks = []
    event = None
    saw_error = ""
    try:
        with no_proxy_opener().open(request, timeout=timeout) as response:
            for raw in response:
                line = raw.decode("utf-8", errors="replace").rstrip("\r\n")
                if line.startswith("event:"):
                    event = line[6:].strip()
                elif line.startswith("data:"):
                    data = line[5:].lstrip()
                    if data == "[DONE]":
                        break
                    if event == "error":
                        saw_error = data
                    elif event == "message" and data:
                        if ttft_ms is None:
                            ttft_ms = round((time.perf_counter() - started) * 1000, 2)
                        chunks.append(data)
                elif not line:
                    event = None
        total_ms = round((time.perf_counter() - started) * 1000, 2)
        if saw_error:
            return False, ttft_ms, total_ms, "".join(chunks), saw_error
        if not chunks:
            return False, ttft_ms, total_ms, "", "SSE 正常结束，但未收到 message 事件"
        return True, ttft_ms, total_ms, "".join(chunks), ""
    except (urllib.error.URLError, socket.timeout, TimeoutError) as error:
        total_ms = round((time.perf_counter() - started) * 1000, 2)
        return False, ttft_ms, total_ms, "".join(chunks), f"超时或断流: {error}"
    except Exception as error:
        total_ms = round((time.perf_counter() - started) * 1000, 2)
        return False, ttft_ms, total_ms, "".join(chunks), str(error)


def main():
    manifest = latest_manifest()
    if not manifest.get("ingestionCompleted"):
        raise RuntimeError("语料尚未全部完成向量化，请重新运行 setup.ps1 或检查处理记录。")
    token = manifest["userToken"]
    knowledge_base_id = manifest["knowledgeBaseId"]
    model = os.getenv("RAG_EVAL_MODEL", "chat-fast")
    timeout = int(os.getenv("RAG_EVAL_SSE_TIMEOUT_SECONDS", "120"))
    questions = [question for question in read_questions() if question["generation_sample"]]
    assert len(questions) == 40
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    run_id = timestamp()
    rows = []
    for question in questions:
        for round_number in range(1, 4):
            print(f"生成评测: {question['id']} round={round_number}", flush=True)
            success, ttft, total, answer, error = stream_answer(token, {
                "prompt": question["question"],
                "model": model,
                "knowledgeBaseIds": [knowledge_base_id],
                "maxRetrievedDocs": 5,
                "minSimilarity": 0.3,
                "clientMessageId": f"rag-eval-{run_id}-{question['id']}-{round_number}",
                "saveMessage": False,
                "streaming": True,
            }, timeout)
            refusal = is_refusal(answer)
            rows.append({
                "questionId": question["id"],
                "type": question["type"],
                "answerable": question["answerable"],
                "round": round_number,
                "success": success,
                "ttftMs": ttft,
                "totalMs": total,
                "keywordCoverage": keyword_coverage(answer, question["acceptable_keywords"]),
                "refusal": refusal,
                "answer": answer,
                "error": error,
            })

    by_question = defaultdict(list)
    for row in rows:
        by_question[row["questionId"]].append(row)
    instability = []
    for question_id, attempts in by_question.items():
        coverages = [attempt["keywordCoverage"] for attempt in attempts if attempt["success"]]
        refusals = {attempt["refusal"] for attempt in attempts if attempt["success"]}
        if coverages and (max(coverages) - min(coverages) >= 0.5 or len(refusals) > 1):
            instability.append(question_id)
    successful = [row for row in rows if row["success"]]
    positive = [row for row in successful if row["answerable"]]
    negative = [row for row in successful if not row["answerable"]]
    summary = {
        "requests": len(rows),
        "successRate": round(len(successful) / len(rows), 4),
        "positiveKeywordCoverage": round(sum(row["keywordCoverage"] for row in positive) / len(positive), 4) if positive else 0,
        "negativeCorrectRefusalRate": round(sum(row["refusal"] for row in negative) / len(negative), 4) if negative else 0,
        "unexpectedRefusalRate": round(sum(row["refusal"] for row in positive) / len(positive), 4) if positive else 0,
        "ttftP50Ms": percentile((row["ttftMs"] for row in successful if row["ttftMs"] is not None), 0.50),
        "ttftP95Ms": percentile((row["ttftMs"] for row in successful if row["ttftMs"] is not None), 0.95),
        "totalP50Ms": percentile((row["totalMs"] for row in successful), 0.50),
        "totalP95Ms": percentile((row["totalMs"] for row in successful), 0.95),
        "unstableQuestionIds": instability,
    }
    output = RESULTS_DIR / f"generation-{run_id}.json"
    csv_output = RESULTS_DIR / f"generation-{run_id}.csv"
    output.write_text(json.dumps({"runId": run_id, "model": model, "summary": summary, "rows": rows}, ensure_ascii=False, indent=2), encoding="utf-8")
    write_csv(csv_output, rows)
    write_pointer("latest-generation.txt", output)
    print(f"生成评测完成: {output}", flush=True)


if __name__ == "__main__":
    main()
