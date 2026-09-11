# Knowledge Ingestion Module Interface

## Purpose

The ingestion Module owns document-version creation, stage idempotency, leases,
retry classification, activation, and terminal state. RabbitMQ is an Adapter
that carries commands; it does not decide whether a stage is complete or which
document version is active.

## Stable concepts

- `DocumentVersion` is an immutable generation of parsed content and chunks.
- `IngestionRun` coordinates one target version through `PARSE`, `CHUNK`, and
  `EMBED`.
- A stage may complete only while holding its current lease token.
- A new version becomes active only after every required stage and its search
  projection have completed.
- Failure while building a new version must leave the previous active version
  readable.

## Compatibility seam

V8 is an expand-only migration. It creates generation 1 for existing documents
and adds nullable `runId`, `versionId`, and `generation` fields to database rows
and RabbitMQ payloads. Legacy producers and consumers remain valid while the
new read and write paths are disabled.

V8 intentionally retains the legacy unique key on
`knowledge_document_chunks(document_id, chunk_index)`. A later migration may
replace it with `(document_id, generation, chunk_index)` only after every chunk
writer always supplies a non-null generation. Until that gate is met, the
application must not create a second generation of chunks.

Generation allocation is not exposed as a `MAX(generation) + 1` repository
operation. The IngestionRun Implementation must allocate it atomically inside
the command transaction and rely on the database unique constraint as the
final concurrency guard.

## Rollout gate

`knowledge-base.processing.versioned-ingestion-enabled` defaults to `false`.
It must remain disabled until all of the following are true:

- chunk creation, lookup, embedding, and deletion are scoped by version and
  generation;
- the legacy chunk unique key has been replaced only after every writer always
  supplies a non-null generation;
- the completed version and its retrieval projection can be activated without
  exposing a BUILDING generation;
- reparse and re-embed failures demonstrably leave the previous active version
  readable.

The presence of V8 rows, atomic command acceptance, and fenced stage leases is
not by itself permission to enable the new path.

## Result contract for the RabbitMQ Adapter

The stage-execution Interface returns one of these structured outcomes:

- `Completed`: ACK the delivery.
- `AlreadyCompleted`: ACK the duplicate delivery.
- `LeaseHeld`: defer or retry without running the stage.
- `RetryableFailure`: ACK only after a retry command is durably accepted;
  otherwise NACK and requeue.
- `PermanentFailure`: ACK only after the terminal failure is durably recorded.

Transport confirmation proves only that RabbitMQ accepted a message. It never
proves that a processing stage completed.

## Implemented compatibility and cleanup paths (2026-09-07)

V10 is a separate migration. It refuses null/nonpositive chunk generations and
unfinished legacy work before replacing the chunk unique key with
`(document_id, generation, chunk_index)`. V8 is unchanged. The migration guard
has been exercised against an isolated MySQL database; this is not a production
migration or authorization to enable versioning in production.

`IngestionRunCommandService` accepts initial ingestion, reparse and re-embed under
the same document lock. Same-action duplicates reuse the active run; conflicting
actions fail explicitly. `VersionedDocumentProcessor` writes generation-scoped
content and stable chunk/vector identities, then waits for searchable projections
before switching the active version in a fenced transaction. Retired versions
are accepted into persistent cleanup tasks. Empty content completes with
embedding status `SKIPPED`, not a claim that vectors exist.

HTTP and internal document/knowledge-base deletion now use
`KnowledgeCleanupCommands`. Acceptance hides the resource, cancels active work
and records a durable task. HTTP DELETE returns 202 with the operation. Cleanup
progress, bounded retries and administrator retry are handled separately; no
caller may infer physical deletion from acceptance. `KnowledgeCleanupWorker`
waits for quiescence, cleans external stores, checks them again, and only then
removes business rows. Failures retain hidden resources and safe task status.

Legacy chunks with null version IDs are eligible only when generation is 1 and
the document has neither an ingestion run nor a generation above 1. This rule
is shared by chunk reads, search projections and retrieval visibility. Existing
multi-version documents cannot use this fallback when the feature flag is off.

See `reliability-acceptance.md` for the tested scenarios and remaining gates.
