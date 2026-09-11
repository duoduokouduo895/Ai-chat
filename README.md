# Dduo AI-Chat

Dduo AI-Chat is a Java 21 modular-monolith application with two Vue 3 clients.
It combines streaming chat, conversation history, knowledge ingestion, hybrid
retrieval, search, account management, and operational tooling in one deployable
backend.

## Repository layout

| Path | Purpose |
| --- | --- |
| `ai-chat-kernel` | Framework-independent shared domain types |
| `ai-chat-identity` | Identity domain contracts |
| `ai-chat-conversation` | Conversation and ChatTurn contracts |
| `ai-chat-knowledge` | Ingestion and retrieval contracts |
| `ai-chat-search` | Search contracts |
| `ai-chat-adapters` | Infrastructure adapters |
| `ai-chat-web` | HTTP, SSE, authentication, and web contracts |
| `ai-chat-app` | Spring Boot composition root and application implementation |
| `frontend` | User-facing Vue 3 workspace |
| `frontend_admin` | Administrative Vue 3 workspace |
| `deploy` | Versioned deployment scripts and templates |
| `perf` | Performance test definitions and scripts |
| `rag-eval` | Reproducible retrieval and generation evaluation |
| `scripts` | Integration and security checks |
| `sql` | Reference SQL assets |

Generated builds, local deployment state, agent work records, and acceptance
artifacts are intentionally excluded from Git.

## Architecture

The backend runs as one Spring Boot process and uses Maven modules to enforce
dependency direction. MySQL is the business source of truth; Redis holds
short-lived state, Elasticsearch stores rebuildable search projections, and
RabbitMQ transports asynchronous work.

- [Product principles](PRODUCT.md)
- [Design system](DESIGN.md)
- [Domain glossary](CONTEXT.md)
- [Architecture decisions](docs/adr/)
- [Capability and module notes](docs/modules/)

## Local checks

Prerequisites: JDK 21, Maven, and a current Node.js 22 release.

```bash
mvn --batch-mode --no-transfer-progress -DskipTests package
```

```bash
cd frontend
npm ci
npm run test:unit
npm run build
```

```bash
cd frontend_admin
npm ci
npm run build
```

These checks validate source and build contracts. Running the full backend and
integration suites also requires the configured infrastructure dependencies.
Copy example configuration files for local setup and keep real credentials out
of the repository.

## Deployment

Backend deployment contracts and safety gates are documented in
[`deploy/README.md`](deploy/README.md). The user and admin frontends are built
and published independently. A successful local build or CI run does not by
itself prove that a production deployment completed.
