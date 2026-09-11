# Conversation Context Module Interface

`ai-chat-conversation` owns `ConversationContext`, its input/output records and
`CompletedContextTurn`. The Interface loads completed history, applies the input
budget and returns the selected input and whole historical turns. Its contract
uses Java `CompletionStage`, with no database, Redis, Spring or model SDK types.

`ConversationContextLoader` is the application Adapter. MySQL supplies completed
ChatTurn pairs; legacy messages are paired only under the conservative ordering
and boundary rules. Model choice never partitions the history. Request-local
model memory is initialized from this result. The old Redis memory cleanup path
remains compatible, but Redis is not the source of model history.

The request contains a full input followed by progressively reduced evidence
candidates. Every candidate preserves the current question. The Adapter accounts
for the system prompt and tool descriptions, removes oldest complete historical
turns first, and reduces evidence only when the fixed input itself does not fit.
If no candidate fits, it reports `CHAT_INPUT_TOO_LARGE`; it does not truncate the
question or restore failed/cancelled turns.

`InputBudgetStreamingChatModel` checks the complete request before every provider
call, including requests augmented with accumulated tool results. Tool output
reservation helps the initial allocation but does not replace this final check.
The configured tokenizer is used when available; the fallback counts UTF-8 bytes
conservatively. SDK message representations include formatting overhead, so the
last check may reject an input that passed the preliminary estimate. These are
estimates, not supplier-reported usage or a promise of improved answer quality.

RAG evidence belongs only to the current input. Persistence records the original
question, final answer, retrieval outcome and source identifiers separately.
Source reads recheck access and availability. Model context never reuses old
augmented prompts as historical user messages.

Validation references: `ConversationContextLoaderTest`,
`InputBudgetStreamingChatModelTest`, `ChatTurnMySqlIntegrationTest` and the
conversation-domain ArchUnit rule. Run status belongs in
`reliability-acceptance.md`, not in this Interface definition.
