# AI-chat 文档入库手册

## 处理主线

[FACT-INGEST-001] 文档上传后的入库主线是 `parse -> chunk -> embed -> store`：先解析文本，再切分 Chunk，然后调用 Embedding 模型生成向量，最后存入向量索引。

[FACT-INGEST-002] 文档处理通过异步消息推进。上传成功只代表任务已受理，不能直接等同于向量化已经完成。

[FACT-INGEST-003] 默认最大 Chunk 长度为 500 个字符，相邻 Chunk 默认保留 50 个字符的 Overlap。

[FACT-INGEST-004] 文档向量化状态接口会返回总 Chunk 数、已完成 Chunk 数、进度百分比和各状态数量，可用于判断是否允许开始评测。
