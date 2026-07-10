#!/usr/bin/env python3
"""Build a Markdown report from the latest retrieval and generation runs."""

from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path

from common import LOCAL_DIR, RESULTS_DIR, read_questions, timestamp, write_csv


def read_latest(pointer_name):
    pointer = LOCAL_DIR / pointer_name
    if not pointer.exists():
        return None
    path = Path(pointer.read_text(encoding="utf-8").strip())
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else None


def main():
    retrieval = read_latest("latest-retrieval.txt")
    generation = read_latest("latest-generation.txt")
    if not retrieval:
        raise RuntimeError("缺少检索评测结果，请先运行 run-retrieval-eval.py。")
    questions = {question["id"]: question for question in read_questions()}
    baseline = {row["questionId"]: row for row in retrieval["rows"] if row["config"] == "baseline"}
    wide = {row["questionId"]: row for row in retrieval["rows"] if row["config"] == "wide"}
    failures = []

    for question_id, row in baseline.items():
        category = None
        if row["error"]:
            category = "INFRA_OR_PROVIDER_ERROR"
        elif row["answerable"] and not row["hitAtK"]:
            category = "TOP_K_OR_THRESHOLD_ISSUE" if wide[question_id]["hitAtK"] else "RETRIEVAL_MISS"
        elif not row["answerable"] and row["negativeFalsePositive"]:
            category = "RETRIEVAL_FALSE_POSITIVE"
        if category:
            failures.append({"questionId": question_id, "stage": "retrieval", "category": category, "detail": row["error"] or row["requiredMarkers"]})

    if generation:
        unstable = set(generation["summary"]["unstableQuestionIds"])
        for row in generation["rows"]:
            category = None
            if not row["success"]:
                category = "INFRA_OR_PROVIDER_ERROR"
            elif row["answerable"] and row["keywordCoverage"] < 0.5:
                category = "GENERATION_MISS"
            elif not row["answerable"] and not row["refusal"]:
                category = "POSSIBLE_HALLUCINATION"
            elif row["questionId"] in unstable:
                category = "GENERATION_INSTABILITY"
            if category:
                failures.append({"questionId": row["questionId"], "stage": "generation", "category": category, "detail": row["error"] or row["answer"]})

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    run_id = timestamp()
    failure_path = RESULTS_DIR / f"failures-{run_id}.csv"
    write_csv(failure_path, failures)
    lines = [
        "# AI-chat RAG 效果评测报告", "",
        f"- 检索运行: `{retrieval['runId']}`",
        f"- 生成运行: `{generation['runId'] if generation else '未执行'}`",
        f"- 待人工复核失败项: `{len(failures)}`", "",
        "## 检索层", "",
        "| 配置 | 问题数 | 错误数 | Precision@K | Recall@K | Hit Rate@K | MRR | 负样本误召回率 | P95 |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for row in retrieval["summary"]:
        lines.append(f"| {row['config']} | {row['questions']} | {row['errors']} | {row['precisionAtK']:.4f} | {row['recallAtK']:.4f} | {row['hitRateAtK']:.4f} | {row['mrr']:.4f} | {row['negativeFalsePositiveRate']:.4f} | {row['latencyP95Ms']}ms |")
    lines += [
        "", "### 按题型分组", "",
        "| 配置 | 题型 | 问题数 | 错误数 | Precision@K | Recall@K | Hit Rate@K | MRR |",
        "|---|---|---:|---:|---:|---:|---:|---:|",
    ]
    for row in retrieval["summaryByType"]:
        lines.append(f"| {row['config']} | {row['type']} | {row['questions']} | {row['errors']} | {row['precisionAtK']:.4f} | {row['recallAtK']:.4f} | {row['hitRateAtK']:.4f} | {row['mrr']:.4f} |")
    lines += ["", "## 生成层", ""]
    if generation:
        summary = generation["summary"]
        lines += [
            f"- SSE 完成率: `{summary['successRate']:.4f}`",
            f"- 正样本关键词覆盖率: `{summary['positiveKeywordCoverage']:.4f}`",
            f"- 负样本正确拒答率: `{summary['negativeCorrectRefusalRate']:.4f}`",
            f"- 非预期拒答率: `{summary['unexpectedRefusalRate']:.4f}`",
            f"- TTFT P50 / P95: `{summary['ttftP50Ms']}ms / {summary['ttftP95Ms']}ms`",
            f"- 完整耗时 P50 / P95: `{summary['totalP50Ms']}ms / {summary['totalP95Ms']}ms`",
            f"- 波动问题数: `{len(summary['unstableQuestionIds'])}`",
        ]
    else:
        lines.append("尚未执行生成评测。")
    lines += [
        "", "## 失败归因", "",
        "| 分类 | 数量 |",
        "|---|---:|",
    ]
    counts = defaultdict(int)
    for failure in failures:
        counts[failure["category"]] += 1
    for category in sorted(counts):
        lines.append(f"| {category} | {counts[category]} |")
    lines += [
        "", "## 说明", "",
        "- `POSSIBLE_HALLUCINATION` 仅表示需要人工复核，不是自动裁决。",
        "- `RETRIEVAL_FALSE_POSITIVE` 表示负样本召回了相似 Chunk，不等同于模型已经产生幻觉。",
        "- 当前系统未显式返回引用，Faithfulness 需要结合检索结果与回答文本人工抽检。",
        f"- 失败明细: `{failure_path.name}`",
    ]
    report = RESULTS_DIR / f"report-{run_id}.md"
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"报告已生成: {report}")


if __name__ == "__main__":
    main()
