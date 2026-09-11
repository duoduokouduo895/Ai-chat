#!/usr/bin/env python3
"""Run deterministic retrieval A/B evaluation against /api/knowledge/search."""

from __future__ import annotations

import json
import time
from collections import defaultdict

from common import RESULTS_DIR, json_request, latest_manifest, marker_hits, mean, percentile, read_questions, timestamp, write_csv, write_pointer
from common import run_evidence

CONFIGS = [
    {"name": "baseline", "maxResults": 5, "minScore": 0.3},
    {"name": "strict", "maxResults": 5, "minScore": 0.7},
    {"name": "wide", "maxResults": 10, "minScore": 0.3},
]


def evaluate(question, config, matches, search_time, elapsed_ms, error=""):
    required = list(dict.fromkeys(question["required_evidence_markers"]))
    hits = marker_hits(matches, required)
    true_positive = sum(bool(marker_hits([match], required)) for match in matches)
    false_negative = max(0, len(required) - len(hits))
    false_positive = sum(1 for match in matches if not marker_hits([match], required))
    rank = next((index for index, match in enumerate(matches, 1) if marker_hits([match], required)), None)
    return {
        "questionId": question["id"],
        "type": question["type"],
        "answerable": question["answerable"],
        "config": config["name"],
        "maxResults": config["maxResults"],
        "minScore": config["minScore"],
        "returnedCount": len(matches),
        "evidenceCoverage": round(len(hits) / len(required), 4) if required and not error else None,
        "tp": true_positive,
        "fp": false_positive,
        "fn": false_negative,
        "precisionAtK": (round(true_positive / len(matches), 4) if matches else 0.0) if not error else None,
        "recallAtK": (round(len(hits) / len(required), 4) if required else (1.0 if not matches else 0.0)) if not error else None,
        "hitAtK": (bool(hits) if required else not matches) if not error else None,
        "mrr": round(1 / rank, 4) if rank else 0.0,
        "negativeFalsePositive": bool(not required and matches) if not error else None,
        "searchTimeMs": search_time,
        "clientElapsedMs": elapsed_ms,
        "requiredMarkers": required,
        "hitMarkers": hits,
        "matches": matches,
        "error": error,
    }


def summarize(rows):
    summary = []
    for config in CONFIGS:
        selected = [row for row in rows if row["config"] == config["name"]]
        positive = [row for row in selected if row["answerable"] and not row["error"]]
        negative = [row for row in selected if not row["answerable"] and not row["error"]]
        successful = [row for row in selected if not row["error"]]
        summary.append({
            "config": config["name"],
            "questions": len(selected),
            "errors": sum(1 for row in selected if row["error"]),
            "precisionAtK": mean(row["precisionAtK"] for row in positive),
            "recallAtK": mean(row["recallAtK"] for row in positive),
            "hitRateAtK": mean(float(row["hitAtK"]) for row in positive),
            "mrr": mean(row["mrr"] for row in positive),
            "negativeFalsePositiveRate": mean(float(row["negativeFalsePositive"]) for row in negative),
            "latencyP50Ms": percentile((row["clientElapsedMs"] for row in successful), 0.50),
            "latencyP95Ms": percentile((row["clientElapsedMs"] for row in successful), 0.95),
            "latencyP99Ms": percentile((row["clientElapsedMs"] for row in successful), 0.99),
        })
    return summary


def summarize_by_type(rows):
    summary = []
    keys = sorted({(row["config"], row["type"]) for row in rows})
    for config_name, question_type in keys:
        selected = [row for row in rows if row["config"] == config_name and row["type"] == question_type]
        successful = [row for row in selected if not row["error"]]
        summary.append({
            "config": config_name,
            "type": question_type,
            "questions": len(selected),
            "errors": sum(1 for row in selected if row["error"]),
            "precisionAtK": mean(row["precisionAtK"] for row in successful),
            "recallAtK": mean(row["recallAtK"] for row in successful),
            "hitRateAtK": mean(float(row["hitAtK"]) for row in successful),
            "mrr": mean(row["mrr"] for row in successful),
        })
    return summary


def main():
    manifest = latest_manifest()
    if not manifest.get("ingestionCompleted"):
        raise RuntimeError("语料尚未全部完成向量化，请重新运行 setup.ps1 或检查处理记录。")
    token = manifest["userToken"]
    knowledge_base_id = manifest["knowledgeBaseId"]
    questions = read_questions()
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    run_id = timestamp()
    rows = []
    for config in CONFIGS:
        print(f"运行检索评测: {config['name']} ({config['maxResults']=}, {config['minScore']=})", flush=True)
        for question in questions:
            started = time.perf_counter()
            try:
                response = json_request("/api/knowledge/search", token=token, method="POST", body={
                    "query": question["question"],
                    "knowledgeBaseIds": [knowledge_base_id],
                    "maxResults": config["maxResults"],
                    "minScore": config["minScore"],
                }, timeout=45)
                matches = response.get("matches", [])
                elapsed = round((time.perf_counter() - started) * 1000, 2)
                rows.append(evaluate(question, config, matches, response.get("searchTime"), elapsed))
            except Exception as error:
                elapsed = round((time.perf_counter() - started) * 1000, 2)
                rows.append(evaluate(question, config, [], None, elapsed, str(error)))

    output = RESULTS_DIR / f"retrieval-{run_id}.json"
    csv_output = RESULTS_DIR / f"retrieval-{run_id}.csv"
    payload = {"runId": run_id, "configs": CONFIGS, "summary": summarize(rows), "summaryByType": summarize_by_type(rows), "rows": rows}
    payload["evidence"] = run_evidence(CONFIGS)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    write_csv(csv_output, [{key: value for key, value in row.items() if key != "matches"} for row in rows])
    write_pointer("latest-retrieval.txt", output)
    print(f"检索评测完成: {output}", flush=True)


if __name__ == "__main__":
    main()
