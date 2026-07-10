#!/usr/bin/env python3
"""Generate the deterministic RAG evaluation question set."""

from __future__ import annotations

import json
from pathlib import Path


FACTS = [
    ("AUTH-001", "普通用户如何携带 JWT 访问受保护 API？", "使用 Authorization: Bearer <token> Header。", ["Authorization", "Bearer", "Header"]),
    ("AUTH-002", "管理员 JWT 可以直接当作普通用户聊天 Token 使用吗？", "不应这样使用，管理员和普通用户使用不同入口与权限。", ["不", "管理员", "普通用户"]),
    ("AUTH-003", "用户退出登录后，旧 Token 还能继续访问受保护接口吗？", "不能，服务端会撤销对应 Token。", ["不能", "撤销", "Token"]),
    ("AUTH-004", "游客账号适合保存长期正式资料吗？", "不适合，游客账号主要用于临时体验。", ["不适合", "临时", "体验"]),
    ("KB-001", "私有知识库允许哪些角色读取？", "仅所有者和管理员可以读取。", ["所有者", "管理员"]),
    ("KB-002", "什么状态的知识库可以参与 RAG 检索？", "ACTIVE 状态。", ["ACTIVE"]),
    ("KB-003", "显式选择知识库时系统会校验什么？", "校验知识库是否存在以及当前用户是否具有读取权限。", ["存在", "权限"]),
    ("KB-004", "没有显式选择知识库时系统会检索哪些知识库？", "最近 5 个公开且 ACTIVE 的知识库。", ["5", "公开", "ACTIVE"]),
    ("INGEST-001", "知识库文档上传后的完整处理步骤是什么？", "parse -> chunk -> embed -> store。", ["parse", "chunk", "embed", "store"]),
    ("INGEST-002", "上传接口返回成功是否代表文档已经完成向量化？", "不是，只代表异步任务已经受理。", ["不是", "异步", "受理"]),
    ("INGEST-003", "默认 Chunk 长度和 Overlap 分别是多少？", "最大 Chunk 长度为 500 个字符，Overlap 为 50 个字符。", ["500", "50"]),
    ("INGEST-004", "如何判断一份文档是否可以开始 RAG 评测？", "查询向量化状态，确认所有 Chunk 已完成向量化。", ["Chunk", "完成", "向量"]),
    ("RAG-001", "RAG 检索怎样找到和问题相关的文本？", "先将问题转换为 Embedding，再执行 Elasticsearch 向量相似度搜索。", ["Embedding", "Elasticsearch", "向量"]),
    ("RAG-002", "主聊天 RAG 默认取多少个 Chunk，最小相似度是多少？", "最多 5 个 Chunk，最小相似度为 0.3。", ["5", "0.3"]),
    ("RAG-003", "知识库检索结果会返回哪些用于评测的关键字段？", "会返回 chunkId、documentId、knowledgeBaseId、内容、分数和 Chunk 序号。", ["chunkId", "documentId", "score"]),
    ("RAG-004", "当前 RAG 检索是否启用了 Rerank？", "没有启用，当前按相似度排序并截取 Top-K。", ["没有", "相似度", "Top-K"]),
    ("MODEL-001", "Nacos 中聊天模型的 type 应配置为什么？", "chat。", ["chat"]),
    ("MODEL-002", "当前默认聊天模型别名是什么？", "chat-fast。", ["chat-fast"]),
    ("MODEL-003", "哪个模型别名负责将文本转换成向量？", "embedding-default。", ["embedding-default"]),
    ("MODEL-004", "模型配置缺少 type 时服务会怎样处理？", "临时按 CHAT 类型兼容加载并输出迁移警告。", ["CHAT", "兼容", "警告"]),
    ("MON-001", "评测前后应通过哪个端点确认服务健康？", "/actuator/health。", ["/actuator/health"]),
    ("MON-002", "性能压测和 RAG 效果评测关注点分别是什么？", "性能压测关注 TPS、P95 和错误率；效果评测关注召回、拒答和可信度。", ["TPS", "P95", "召回", "拒答"]),
    ("MON-003", "TTFT 指标表示什么？", "从发送请求到收到首条有效回答片段的时间。", ["首条", "回答", "时间"]),
    ("MON-004", "为什么报告要区分外部供应商错误和应用自身错误？", "外部模型或 Embedding API 延迟和异常也会影响结果，需要单独归因。", ["外部", "Embedding", "归因"]),
    ("TROUBLE-001", "知识库召回异常时应该先排查哪些环节？", "先确认解析和向量化，再检查切分、Embedding、Top-K 和阈值。", ["解析", "向量", "Top-K", "阈值"]),
    ("TROUBLE-002", "正确证据已进入 Top-K 但答案仍错，应该优先排查什么？", "优先排查 Prompt、模型理解和幻觉问题。", ["Prompt", "模型", "幻觉"]),
    ("TROUBLE-003", "没有相关证据却给出确定答案，应如何处理？", "标记为疑似幻觉并人工复核。", ["疑似幻觉", "人工", "复核"]),
    ("TROUBLE-004", "HTTPS 页面调用 HTTP API 被拦截通常是什么原因？", "浏览器 mixed-content 策略导致，应使用相对路径 /api。", ["mixed-content", "/api"]),
    ("RELEASE-001", "当前推荐的统一聊天入口是什么？", "/api/ai/chat。", ["/api/ai/chat"]),
    ("RELEASE-002", "统一聊天 SSE 事件发送顺序是什么？", "先发送 metadata，再发送一个或多个 message。", ["metadata", "message"]),
    ("RELEASE-003", "首版 RAG 效果评测包含多少问题，负样本比例是多少？", "120 条问题，其中 20% 为负样本。", ["120", "20%"]),
    ("RELEASE-004", "RAG 评测后服务端测试资源应如何清理？", "展示清单并单独确认后，精确删除账号、知识库和文档。", ["清单", "确认", "删除"]),
]

PARAPHRASES = [
    ("AUTH-003", "我退出账号以后，之前那串 JWT 还能接着用不？", "不能，退出后 Token 会被撤销。", ["不能", "撤销"]),
    ("KB-004", "用户啥知识库都没选时，RAG 默认去哪找资料？", "只查最近 5 个公开且 ACTIVE 的知识库。", ["5", "公开", "ACTIVE"]),
    ("INGEST-001", "文件丢进知识库后，后台依次做哪几步？", "依次执行 parse、chunk、embed 和 store。", ["parse", "chunk", "embed", "store"]),
    ("INGEST-002", "上传按钮提示成功了，是不是马上就能问知识库？", "不一定，上传成功只表示异步任务受理，还要等待向量化完成。", ["不", "异步", "向量"]),
    ("INGEST-003", "切片默认多长，前后片段会重叠多少字？", "Chunk 最大 500 字符，Overlap 50 字符。", ["500", "50"]),
    ("RAG-001", "RAG 是咋从 ES 里捞到相关段落的？", "先把问题向量化，再做 Elasticsearch 相似度检索。", ["向量", "Elasticsearch"]),
    ("RAG-002", "聊天时默认召回几个片段，分数门槛是多少？", "默认最多 5 个片段，门槛 0.3。", ["5", "0.3"]),
    ("RAG-004", "咱们现在有二次排序吗？", "当前没有启用 Rerank。", ["没有", "Rerank"]),
    ("MODEL-002", "日常聊天默认走哪个模型别名？", "chat-fast。", ["chat-fast"]),
    ("MODEL-004", "Nacos 老格式没写 type，服务会直接挂吗？", "不会，会临时按 CHAT 兼容加载并告警。", ["不会", "CHAT", "告警"]),
    ("MON-001", "跑评测之前看哪个健康检查地址？", "/actuator/health。", ["/actuator/health"]),
    ("MON-003", "TTFT 到底量的是哪段时间？", "从请求发出到首条有效回答片段到达。", ["首条", "回答"]),
    ("TROUBLE-001", "召回结果不对，排障顺序怎么走？", "先查解析和向量化，再查 Chunk、Embedding、Top-K 与阈值。", ["解析", "向量", "Top-K"]),
    ("TROUBLE-002", "证据都搜到了，模型还是答错，锅大概率在哪？", "优先检查 Prompt、模型理解和幻觉。", ["Prompt", "模型", "幻觉"]),
    ("TROUBLE-004", "线上 HTTPS 页面请求接口报 mixed-content，怎么改？", "生产前端使用相对路径 /api。", ["mixed-content", "/api"]),
    ("RELEASE-001", "现在聊天请求应该发到哪个 API？", "/api/ai/chat。", ["/api/ai/chat"]),
    ("RELEASE-002", "SSE 开始时先来 metadata 还是 message？", "先 metadata，后 message。", ["metadata", "message"]),
    ("RELEASE-004", "测完以后可以直接把测试库删掉吗？", "先展示清单并单独确认，再精确删除。", ["清单", "确认"]),
]

MULTI = [
    (["AUTH-001", "AUTH-003"], "说明普通用户 JWT 的携带方式以及退出登录后的处理。", "JWT 放在 Authorization Bearer Header 中；退出后服务端撤销 Token。", ["Authorization", "Bearer", "撤销"]),
    (["KB-001", "KB-002"], "私有知识库满足什么条件时可以被读取并参与检索？", "读取者需要是所有者或管理员，且知识库状态为 ACTIVE。", ["所有者", "管理员", "ACTIVE"]),
    (["INGEST-001", "INGEST-004"], "从文档上传到允许开始评测，需要经历什么流程并检查什么状态？", "完成 parse、chunk、embed、store，并确认所有 Chunk 已向量化。", ["parse", "chunk", "embed", "store", "Chunk"]),
    (["RAG-001", "RAG-004"], "描述当前向量检索和排序流程。", "问题先 Embedding，再查 Elasticsearch，按相似度排序后截取 Top-K，当前无 Rerank。", ["Embedding", "Elasticsearch", "Top-K", "Rerank"]),
    (["MON-002", "MON-003"], "RAG 效果评测和性能观测分别需要看哪些指标？", "效果看召回、拒答和可信度；生成性能还需记录 TTFT，压测看 TPS、P95 和错误率。", ["召回", "拒答", "TTFT", "TPS", "P95"]),
    (["TROUBLE-001", "TROUBLE-002"], "如何区分召回失败和模型生成失败？", "先看正确证据是否进入 Top-K；未进入是检索问题，已进入仍答错则检查生成链路。", ["Top-K", "检索", "生成"]),
    (["MODEL-001", "MODEL-004"], "模型目录迁移期间为什么会有警告，应该如何处理？", "缺少 type 时会按 CHAT 兼容加载并警告，应补齐 Nacos type。", ["type", "CHAT", "Nacos"]),
    (["RELEASE-001", "RELEASE-002"], "统一聊天入口和 SSE 事件顺序分别是什么？", "入口是 /api/ai/chat，先 metadata 后 message。", ["/api/ai/chat", "metadata", "message"]),
    (["INGEST-002", "MON-004"], "为什么上传完成后不能立刻评价 RAG，并且报告要记录外部错误？", "上传仅代表异步受理，需要等待向量化；外部 Embedding API 也会影响结果。", ["异步", "向量", "Embedding"]),
    (["KB-003", "KB-004"], "显式选库和不选库时，系统检索范围有什么不同？", "显式选择会校验存在性和权限；未选择只查最近 5 个公开 ACTIVE 库。", ["权限", "5", "公开", "ACTIVE"]),
    (["TROUBLE-003", "RELEASE-004"], "遇到疑似幻觉后，评测资源和失败样本应如何处理？", "失败样本交人工复核；测试资源展示清单并确认后删除。", ["人工", "复核", "清单", "删除"]),
    (["MODEL-002", "MODEL-003"], "默认聊天与向量模型别名分别是什么？", "聊天模型是 chat-fast，向量模型是 embedding-default。", ["chat-fast", "embedding-default"]),
    (["MON-001", "MON-004"], "评测前如何确认服务可用，报告又应如何记录依赖影响？", "用 /actuator/health 检查健康，并区分外部供应商错误与应用错误。", ["/actuator/health", "外部", "应用"]),
    (["RAG-002", "RAG-003"], "主聊天默认召回参数是什么，返回结果中哪些字段便于定位证据？", "默认 Top-5、阈值 0.3，可用 chunkId、documentId 和 score 定位。", ["5", "0.3", "chunkId", "documentId", "score"]),
]

CROSS = [
    (["INGEST-001", "RAG-001"], "文档和问题分别怎样变成可用于检索的向量？", "文档经过 parse、chunk、embed、store；问题检索时也会先转换为 Embedding。", ["chunk", "embed", "store", "Embedding"]),
    (["KB-002", "TROUBLE-001"], "知识库没有召回时，为什么既要看 ACTIVE 状态也要看向量化状态？", "未 ACTIVE 的库不参与检索，未完成向量化的 Chunk 也无法正常召回。", ["ACTIVE", "向量"]),
    (["AUTH-002", "KB-001"], "管理员身份与私有知识库读取权限有什么关系？", "管理员使用独立 JWT，并可以读取私有知识库。", ["管理员", "JWT", "私有"]),
    (["RELEASE-001", "MON-003"], "统一聊天入口上线后，如何观测流式回答的首包速度？", "调用 /api/ai/chat，并记录从请求到首条有效回答片段的 TTFT。", ["/api/ai/chat", "TTFT", "首条"]),
    (["MODEL-003", "MON-004"], "Embedding 模型异常会如何影响评测报告？", "问题和 Chunk 无法稳定向量化，报告应归类为外部供应商或依赖错误。", ["Embedding", "外部", "错误"]),
    (["RAG-004", "TROUBLE-001"], "当前没有 Rerank 时，召回质量不佳可以优先调什么？", "先检查 Chunk、Embedding、Top-K 和相似度阈值。", ["Chunk", "Embedding", "Top-K", "阈值"]),
    (["INGEST-002", "RELEASE-004"], "异步入库对测试资源清理有什么要求？", "先等待处理完成；清理时展示清单并按精确 ID 删除资源。", ["等待", "清单", "删除"]),
    (["KB-004", "RAG-002"], "不选库并使用主聊天默认参数时，检索范围和返回数量是什么？", "从最近 5 个公开 ACTIVE 库中检索，最多返回 5 个 Chunk。", ["5", "公开", "ACTIVE", "Chunk"]),
    (["MON-002", "TROUBLE-003"], "为什么只做性能压测不能发现模型幻觉？", "性能指标只能说明速度和稳定性；无证据仍确定回答需要通过效果评测发现。", ["性能", "效果", "证据"]),
    (["AUTH-003", "RELEASE-004"], "评测完成后为什么既要清理服务端账号，也要移除本地 Token？", "减少残留测试数据，并避免已使用 Token 继续被复用。", ["账号", "Token", "残留"]),
    (["MODEL-001", "RELEASE-001"], "统一聊天入口与 Nacos 模型目录分别解决什么问题？", "统一入口收敛聊天调用；模型目录声明可选模型及类型。", ["入口", "模型", "type"]),
    (["RAG-003", "TROUBLE-002"], "怎样利用检索返回字段判断错误发生在检索还是生成阶段？", "用 chunkId、documentId、score 判断证据是否已召回；已召回仍答错则是生成阶段问题。", ["chunkId", "score", "生成"]),
]

TIME_BOUND = [
    ("RELEASE-001", "在 2026-05 版本之后，推荐使用哪个聊天入口？", "/api/ai/chat。", ["/api/ai/chat"]),
    ("RELEASE-002", "2026-05 版本统一聊天接口的 SSE 事件顺序是什么？", "先 metadata 后 message。", ["metadata", "message"]),
    ("RELEASE-003", "2026-06 首版评测约定的问题数量是多少？", "120 条。", ["120"]),
    ("RELEASE-003", "2026-06 首版评测约定的负样本比例是多少？", "20%。", ["20%"]),
    ("RELEASE-004", "按照 2026-06 评测约定，报告和服务端资源分别如何处理？", "保留本地报告，确认后删除服务端测试资源。", ["报告", "确认", "删除"]),
    ("MODEL-002", "当前手册中默认聊天模型别名是什么？", "chat-fast。", ["chat-fast"]),
    ("MODEL-004", "当前迁移兼容逻辑遇到缺少 type 的模型会怎样处理？", "按 CHAT 临时加载并告警。", ["CHAT", "告警"]),
    ("RAG-004", "当前版本是否已经启用 Rerank？", "没有。", ["没有"]),
]

NOISY = [
    ("RAG-004", "rerank / rank / topk：现在到底有没有二排？", "当前未启用 Rerank。", ["未", "Rerank"]),
    ("INGEST-003", "chunk=??? overlap=??? 默认切片参数求确认", "Chunk 最大 500 字符，Overlap 50 字符。", ["500", "50"]),
    ("MON-003", "TTFT 是 total time 吗，还是 first token time？", "是收到首条有效回答片段所需时间，不是完整耗时。", ["首条", "不是"]),
    ("AUTH-001", "JWT 放 query 还是 header？", "放 Authorization Bearer Header。", ["Authorization", "Bearer", "Header"]),
    ("RAG-002", "top k / min score 默认值？聊天链路", "Top-K 为 5，min score 为 0.3。", ["5", "0.3"]),
    ("MODEL-003", "embedding-default 是 chat model 还是 embedding model？", "是 Embedding 模型。", ["Embedding"]),
    ("KB-002", "kb ACTIVE / inactive，哪个能搜？", "ACTIVE 能参与检索。", ["ACTIVE"]),
    ("RELEASE-002", "SSE: metadata -> message 还是 message -> metadata？", "metadata -> message。", ["metadata", "message"]),
]

NEGATIVE = [
    ("negative", "AI-chat 是否支持使用银行卡自动续费会员？"),
    ("negative", "系统默认每天几点发送日报邮件？"),
    ("negative", "移动端 App 在 iOS 商店的下载地址是什么？"),
    ("negative", "知识库最多允许上传多少个文件？"),
    ("negative", "普通用户每月可以调用模型多少次？"),
    ("negative", "系统是否支持微信扫码登录？"),
    ("negative", "聊天记录默认保留多少天？"),
    ("negative", "生产服务器部署在哪个城市？"),
    ("negative", "管理员密码多久强制修改一次？"),
    ("negative", "系统采用哪一种 OCR 服务？"),
    ("negative", "支持哪些银行卡类型？"),
    ("negative", "客服热线电话号码是多少？"),
    ("near_miss", "主聊天默认最多取 50 个 Chunk 吗？"),
    ("near_miss", "默认 Overlap 是 500 个字符吗？"),
    ("near_miss", "未选择知识库时会搜索最近 50 个私有知识库吗？"),
    ("near_miss", "当前默认聊天模型是不是 embedding-default？"),
    ("near_miss", "Embedding 模型的 type 应配置为 chat 吗？"),
    ("near_miss", "上传成功后是否保证向量化同步完成？"),
    ("near_miss", "HTTPS 页面调用 HTTP API 一定可以正常工作吗？"),
    ("near_miss", "退出登录后 JWT 会永久有效吗？"),
    ("near_miss", "当前推荐继续使用 /api/knowledge/chat 作为主入口吗？"),
    ("near_miss", "当前系统已经启用了 Rerank，对吗？"),
    ("near_miss", "私有知识库允许任意游客直接读取吗？"),
    ("near_miss", "TTFT 是完整回答生成结束所需时间吗？"),
]


def item(index, qtype, markers, question, answer, keywords, generation_sample=False):
    return {
        "id": f"rag-{index:03d}",
        "question": question,
        "type": qtype,
        "answerable": bool(markers),
        "gold_answer": answer,
        "required_evidence_markers": [f"FACT-{marker}" for marker in markers],
        "acceptable_keywords": keywords,
        "forbidden_claims": [],
        "generation_sample": generation_sample,
    }


def main():
    questions = []
    for fact in FACTS:
        questions.append(("single_fact", [fact[0]], fact[1], fact[2], fact[3]))
    for fact in FACTS[:4]:
        questions.append(("single_fact", [fact[0]], f"请简要回答：{fact[1]}", fact[2], fact[3]))
    for fact in PARAPHRASES:
        questions.append(("paraphrase", [fact[0]], fact[1], fact[2], fact[3]))
    for markers, question, answer, keywords in MULTI:
        questions.append(("multi_evidence", markers, question, answer, keywords))
    for markers, question, answer, keywords in CROSS:
        questions.append(("cross_document", markers, question, answer, keywords))
    for marker, question, answer, keywords in TIME_BOUND:
        questions.append(("time_bound", [marker], question, answer, keywords))
    for marker, question, answer, keywords in NOISY:
        questions.append(("noisy_query", [marker], question, answer, keywords))
    for qtype, question in NEGATIVE:
        questions.append((qtype, [], question, "知识库资料不足，无法确认。", []))

    expected = {
        "single_fact": 36,
        "paraphrase": 18,
        "multi_evidence": 14,
        "cross_document": 12,
        "time_bound": 8,
        "noisy_query": 8,
        "negative": 12,
        "near_miss": 12,
    }
    actual = {key: sum(1 for row in questions if row[0] == key) for key in expected}
    assert actual == expected, (actual, expected)
    assert len(questions) == 120

    positive_indices = [i for i, row in enumerate(questions) if row[1]]
    negative_indices = [i for i, row in enumerate(questions) if not row[1]]
    sampled = set(positive_indices[::3][:32] + negative_indices[::3][:8])
    assert len(sampled) == 40

    output = Path(__file__).with_name("questions.jsonl")
    with output.open("w", encoding="utf-8", newline="\n") as handle:
        for offset, row in enumerate(questions, 1):
            handle.write(json.dumps(item(offset, *row, generation_sample=(offset - 1) in sampled), ensure_ascii=False) + "\n")
    print(f"generated {len(questions)} questions -> {output}")


if __name__ == "__main__":
    main()
