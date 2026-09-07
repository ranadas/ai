# Design Prompt — Multi-Agent Platform for Transfer Agency & Fund Administration

> Usage: paste this whole prompt as the first message of a new session, together with the reference documents listed in §8. Fill every `[[...]]` placeholder before sending, or leave it and expect the model to ask.

---

## 1. Your role

You are a principal software architect with deep experience in regulated financial-services platforms (transfer agency, fund administration, KYC/AML) and in production agentic-AI systems. You favour scalable, readable, elegant backend design and deterministic control flow. You are producing a design that a small engineering team, working with AI coding assistants (Claude Code, OpenCode), will implement.

## 2. What we are building

A single coherent enterprise application — UI + backend APIs — for TA/FA operations. Internally it is a multi-agent system (MAS): specialised agents that communicate and coordinate through a coordination layer (orchestrator, protocol, message bus), each agent composed of the four building blocks **Brain (model) · Instructions (prompts/guidelines) · Memory (context) · Tools (actions/perception)**.

The orchestrator assists in decision-making, selects tools to achieve a goal, uses RAG where needed, and runs an evaluation loop with persistent memory so the system improves over time.

**Design stance (non-negotiable):**
- Hybrid, not maximally agentic. Deterministic code owns control flow, validation, financial calculations and integration. LLMs are used for *perception and translation* (reading documents, drafting text, classifying, generating questions) — never as the computational runtime for deterministic logic. Not every functional flow needs an LLM call at all; say explicitly which flows do not.
- Financial and compliance guards live in code, not prompts. Tools accept identifiers (invoice ID, trade file ID, document ID), not free-text figures, so hallucinated values cannot reach a rule engine or a payment file.
- Human-in-the-loop (HITL) is mandatory at **every** exception, validation failure, low-confidence extraction, or pipeline error. Design HITL as a first-class, reusable capability (task queue, review UI, decision audit), not a per-feature afterthought.
- Plugin/module architecture: each feature is a module that plugs into shared platform services by following a fixed contract. Adding feature #5 must require no change to the core. Prove this by showing exactly what a new module must implement.
- No duplicated functionality across agents or modules. You decide the agent decomposition; justify it.
- Vendor-neutral model strategy: a model gateway abstracts providers; the initial provider is an **internal model** (`[[model name / endpoint]]`) that supports text, vision and embeddings — use it for all three; do not introduce a second embedding provider. Model identifiers are pinned explicitly in configuration. LLM vision is used to read text from images — there is no separate OCR component.
- Full audit trail and GDPR compliance (data minimisation, retention, PII handling, right to erasure) across all modules.

## 3. Standout end-user features (the first four modules)

Derive the detailed requirements for each from the attached documents (§8). Summarise your understanding of each before designing.

1. **Questionnaire generator for TA operators** — automated callback/question generation using RAG over indexed procedures (vector DB) + prompt library + internal document repository; document generation engine; outputs to `[[COMBO system]]` and SMTP email.
2. **Trade file processing** — SFTP file watcher (`YYYYMMDD_UGYYYYMMDD.csv`), column/logic validation, CSV → flat-file transformation (replacing an Excel macro), upload to `[[Neolink]]` via SSO, audit/logging, FA-team manual review before market release. Likely no LLM in the happy path.
3. **KYC ID document extraction** — intelligent document processing over identity documents via LLM vision, data normalisation, identity validation and expiry checks, CSV output for downstream posting to `[[KYC/Compliance DB]]`.
4. **Invoice payment processing** — ingestion from group mailbox, document routing, LLM extraction, rule-based validation (ASL verification, amount thresholds), payment-file generation for `[[payment processing application]]`, integration with `[[Neolink, FA system Geneva, NAV Control Pack]]`.

Modules 3 and 4 share an ingestion → router → extraction → normalisation → decision-engine → integration-gateway pipeline; design that pipeline once and reuse it.

## 4. Platform tools & enterprise integrations

Shared tools available to agents: document repository, relational database, vector database, model gateway (internal model), email (IMAP/SMTP), SFTP, and REST/SOAP adapters to downstream systems: `[[list systems, protocol, auth, sync/async, rate limits]]`. Treat every downstream system as an adapter behind an integration gateway with idempotency, retries and a dead-letter path to HITL.

## 5. Technical constraints & context

- Language/runtime: `[[Python 3.x]]`; frontend: Angular (consistent with the existing estate), consuming the backend REST API.
- Hosting: Linux VMs, systemd-managed processes — **not** containers/OpenShift. No Kafka initially; propose the lightest coordination mechanism that still gives an upgrade path to a broker.
- Security: OAuth2/OIDC SSO, RBAC by operator role, secrets management, network zoning between UI, API, agents and downstream systems.
- Existing estate to interoperate with: `[[Spring Boot / Java 17 microservices, WebSphere Liberty, etc.]]`.
- Team: `[[size, seniority, availability]]`; AI coding assistants in daily use.
- Timeline/budget guardrails: `[[if any]]`.

## 6. Process — ask before you design

Before producing any design artefact, list every ambiguity, missing input or conflicting requirement you find (in the documents or this brief) as numbered questions, grouped by topic, each with your proposed default assumption. Stop and wait for answers. Only after answers (or explicit approval of your defaults) produce the deliverables in §7 — all of them, in this single session, in order. Do not stop to ask whether to continue between deliverables; if output length limits force a break, end at a section boundary and resume with the next section when told to continue. If later steps reveal new ambiguity, pause again rather than guess silently.

## 7. Deliverables (produce in this order; each is its own section)

1. **Requirements digest** — per feature: actors, inputs, outputs, business rules, exceptions, downstream systems, LLM-needed vs. deterministic steps.
2. **Logical architecture** — layers (UI, API, orchestration/coordination, agents, platform services, tools/adapters, data), responsibilities, boundaries. Mermaid diagram.
3. **Component architecture** — concrete components per layer, the module plugin contract (interfaces a new feature must implement, registration, configuration), shared services (model gateway, prompt registry, RAG service, HITL service, audit, eval/memory store, integration gateway, scheduler/watchers). Mermaid diagram.
4. **Agent definitions** — for each agent: purpose, role, brain (model + pinned ID), instructions (prompt strategy), memory (what/where/retention), tools, inputs/outputs, guardrails, failure → HITL behaviour. Show which agents are shared and which are module-specific; demonstrate zero duplication.
5. **Workflows / sequence diagrams** — one per feature (happy path + at least one exception path with HITL), plus the generic HITL flow and the eval/feedback loop. Mermaid `sequenceDiagram`.
6. **APIs** — backend REST API (OpenAPI-style outline): resources, endpoints, auth, pagination, async job pattern, webhooks/callbacks; internal agent-to-agent protocol and message schemas.
7. **Data model** — relational entities (ERD in Mermaid), vector DB collections and metadata, document repository layout, audit-log schema, memory/eval store. Note PII fields and retention per entity.
8. **Security & compliance** — authn/authz, PII handling, GDPR controls, prompt-injection and data-exfiltration defences, secrets, audit immutability, segregation of duties for HITL approvals.
9. **Deployment topology** — VM layout, systemd units, process-per-module vs. shared workers, DB/vector DB placement, networking, HA/DR, environments (dev/UAT/prod), observability.
10. **NFRs** — throughput and latency targets per feature, availability, RTO/RPO, scalability path, cost controls on model usage, maintainability metrics.
11. **Evaluation strategy** — golden datasets per feature, extraction accuracy metrics, questionnaire quality rubric, regression evals in CI, online monitoring, HITL decisions fed back into evals and prompt/config tuning (the persistent-memory improvement loop). Distinguish deterministic tests from model evals.
12. **Implementation roadmap** — phases with scope, dependencies, exit criteria; Phase 1 must deliver the platform core + the simplest module end-to-end (recommend which and why).
13. **Estimation** — per phase and per work-stream (design, development, QA, CI/CD, deployment, documentation): person-days, with and without AI-assistant productivity effect (state the multiplier you assume and why), critical-path items, and key risks with mitigations. Show your assumptions in a table.

**Format rules:** Mermaid for all diagrams; tables for agents, APIs, NFRs and estimates; concise prose otherwise. Prefer explicit design decisions with a one-line rationale over generic options lists. Keep each deliverable self-contained so it can be lifted into a design document.

## 8. Attached reference documents

- `[[doc 1 — e.g. Callback Question Generator technical flow]]`
- `[[doc 2 — e.g. Invoice/KYC IDP pipeline flow]]`
- `[[doc 3 — e.g. Trade file processing flow]]`
- `[[doc 4 — e.g. use-case prioritisation pack / management deck]]`

Read all of them before answering. Where a document contradicts this brief, flag it in your questions rather than choosing silently.
