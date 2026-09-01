# Shared AI Capability Service — Design Document

| | |
|---|---|
| **Status** | Draft v0.1 |
| **Date** | 1 September 2026 |
| **Scope** | RAG assistant, KYC identity-document pipeline, invoice payment pipeline |
| **Audience** | Engineering, Architecture Review Board, Risk & Compliance |

---

## 1. Purpose

Three applications currently embed AI/LLM functionality independently:

1. **RAG assistant** — natural-language Q&A over a relational database, backed by an LLM.
2. **KYC pipeline** — Intelligent Document Processing (IDP) extracts datapoints from identity documents and emits a CSV for a downstream system.
3. **Invoice payment pipeline** — IDP extracts datapoints from invoices, applies rule-based validation, and generates payment files for the payment processing application.

This document proposes a single **AI Capability Service** that owns all model interaction, document extraction, retrieval, and AI governance for these applications, while leaving business workflow, rules, and output formats inside each application.

### 1.1 Goals

- One place for model access, prompt/schema versioning, evaluation, audit, and cost control.
- One governance and audit story for regulators, rather than three.
- Consumers interact through **typed capabilities**, never raw prompts.
- No consumer application is coupled to a specific model vendor.

### 1.2 Non-goals

- The service does **not** own KYC acceptance rules, sanctions logic, invoice tolerance thresholds, payment-file formats, or the RAG application's authorisation model.
- The service is **not** a workflow engine or an agent runtime for business processes.
- Replacing the existing IDP vendor is out of scope; the service wraps it initially and may replace it later behind the same contract.

---

## 2. Design principles

| # | Principle | Consequence |
|---|---|---|
| P1 | **Capability, not workflow** | The service exposes verbs like *extract*, *retrieve*, *generate*, *classify*. It never knows what a "valid invoice" or an "accepted KYC case" is. |
| P2 | **Deterministic control flow; LLMs for perception only** | Models produce structured, typed output with confidence and provenance. All decisions are made in consumer code or in explicit, versioned policies. |
| P3 | **No prompt strings cross the boundary** | Consumers reference a `capabilityId` and pass typed inputs. Prompts live in the registry, versioned and reviewed. |
| P4 | **Pinned everything** | Model IDs, prompt versions, schema versions, and embedding model versions are explicit in every request and recorded in every audit entry. Library defaults are never relied upon. |
| P5 | **Financial guards in code, not prompts** | The service never emits free-text amounts or decisions into a rules engine. Extracted numerics are typed, validated against the schema, and carry confidence. |
| P6 | **Isolation by tenant/caller** | KYC documents and invoices have different classification and retention. Storage, corpora, encryption keys, and permissions are segregated per calling application. |
| P7 | **Degrade to queued, not failed** | Long-running work is asynchronous. Service unavailability results in queued jobs, not lost work. |
| P8 | **Every change is a governed release** | A new model, prompt, or schema version ships with evaluation results attached and a rollback path. |

---

## 3. Responsibility boundary

### 3.1 Owned by the AI Capability Service

- **Model gateway** — provider abstraction (Anthropic, OpenAI, Azure OpenAI, on-prem), routing, retries, timeouts, rate limiting, token budgets, per-caller cost attribution.
- **Document extraction** — schema-driven extraction with per-field confidence and source location (page + bounding box). Wraps the existing IDP initially.
- **Retrieval** — embedding generation, index management, hybrid (vector + keyword) retrieval, optional re-ranking, over registered corpora.
- **Capability registry** — versioned prompts, schemas, model bindings, guardrail policies, confidence thresholds.
- **Governance** — PII detection/redaction, content guardrails, immutable audit log, evaluation harness with golden datasets, per-capability kill switch.
- **Human review routing** — threshold policies that mark low-confidence outputs for review; a review-queue API so consumers do not each build one.

### 3.2 Retained by consumers

| Consumer | Retains |
|---|---|
| RAG assistant | Query authorisation (row-level security), conversation state, answer presentation, which corpora a user may query. |
| KYC pipeline | Document acceptance rules, sanctions/PEP checks, case management, CSV contract with downstream. |
| Invoice pipeline | Validation rule engine, tolerances, vendor master matching, payment-file generation, posting to the payment application. |

---

## 4. Architecture overview

```
┌──────────────┐  ┌──────────────┐  ┌──────────────────┐
│ RAG Assistant│  │ KYC Pipeline │  │ Invoice Pipeline │   OAuth2 clients
└──────┬───────┘  └──────┬───────┘  └────────┬─────────┘   (scoped per capability)
       │ sync REST       │ async (Kafka/SQS) │ async
       ▼                 ▼                   ▼
┌─────────────────────────────────────────────────────────┐
│               AI Capability Service (API)               │
│  ┌──────────┐ ┌───────────┐ ┌──────────┐ ┌───────────┐  │
│  │ Gateway  │ │ Extraction│ │ Retrieval│ │ Registry  │  │
│  └────┬─────┘ └─────┬─────┘ └────┬─────┘ └───────────┘  │
│  ┌────┴─────────────┴────────────┴──────────────────┐   │
│  │  Guardrails · PII · Audit · Evaluation · Review   │   │
│  └───────────────────────────────────────────────────┘   │
└──────┬─────────────┬──────────────┬──────────────────────┘
       │             │              │
  LLM providers   IDP vendor   Vector store + Postgres (registry, audit, jobs)
```

### 4.1 Deployment shape

Two Spring Boot deployables sharing one domain module:

- **`ai-gateway`** — synchronous API, registry, audit writes, retrieval, generation.
- **`ai-extraction-worker`** — consumes extraction jobs from the queue, calls IDP/LLM, writes results, emits completion events. Scales independently.

Modules (Maven multi-module or Gradle subprojects): `domain`, `gateway`, `extraction`, `retrieval`, `registry`, `guardrails`, `audit`, `review`, `providers` (one adapter per vendor).

### 4.2 Data stores

| Store | Content | Notes |
|---|---|---|
| Postgres | Capabilities, schemas, prompt versions, model bindings, jobs, review items, audit log | Flyway-managed. Audit table is append-only (no UPDATE/DELETE grants). |
| Vector store (pgvector initially) | Embeddings per corpus | One schema/namespace per corpus; corpus bound to owning caller. |
| Object storage | Document inputs and rendered page images | Bucket per caller; per-caller KMS key; retention policy per corpus/document class. |

---

## 5. API contracts

### 5.1 Conventions

- Base path: `/api/v1`. Path versioning for breaking changes only.
- Authentication: OAuth2 client credentials (OIDC). Scopes are of the form `ai:<capability>:<action>`, e.g. `ai:extract:invoice`, `ai:retrieve:policy-docs`, `ai:review:read`.
- Every request carries:
  - `X-Correlation-Id` — propagated end-to-end and recorded in audit.
  - `Idempotency-Key` — required on all POSTs that create work. Same key + same body → same response; same key + different body → `409`.
- All timestamps ISO-8601 UTC. All monetary values are `{ "amount": "1234.56", "currency": "EUR" }` with amount as a decimal **string**, never a float.
- Errors follow RFC 9457 Problem Details:

```json
{
  "type": "https://ai.internal/problems/schema-not-found",
  "title": "Schema not found",
  "status": 404,
  "detail": "Schema 'invoice' version 3 does not exist",
  "instance": "/api/v1/extractions",
  "correlationId": "9f1c…"
}
```

- Every successful response includes a `provenance` block so consumers can log exactly what produced the output:

```json
"provenance": {
  "capabilityId": "extract.invoice",
  "capabilityVersion": 3,
  "schemaId": "invoice",
  "schemaVersion": 3,
  "model": { "provider": "anthropic", "id": "claude-sonnet-4-6", "pinned": true },
  "promptVersion": "invoice-extract@7",
  "auditId": "aud_01J…"
}
```

### 5.2 Document ingestion

Documents are registered once and referenced by ID thereafter; raw bytes never appear in extraction requests.

**`POST /api/v1/documents`** — multipart upload
Scope: `ai:documents:write`

Form fields: `file` (binary), `metadata` (JSON):

```json
{
  "documentClass": "INVOICE",
  "externalRef": "INV-2026-000482",
  "retention": "P7Y",
  "classification": "CONFIDENTIAL"
}
```

Response `201`:

```json
{
  "documentId": "doc_01J8ZK…",
  "documentClass": "INVOICE",
  "pages": 2,
  "contentHash": "sha256:ab12…",
  "storedAt": "2026-09-01T10:14:02Z",
  "expiresAt": "2033-09-01T10:14:02Z"
}
```

Rules:
- `documentClass` must be one the caller's client is entitled to (`IDENTITY_DOCUMENT` for KYC, `INVOICE` for the invoice pipeline). Cross-class upload → `403`.
- Duplicate `contentHash` within the caller's namespace returns the existing `documentId` with `200` (not `201`).
- Virus scan and PII inventory run on ingest; results attached to the document record.

**`GET /api/v1/documents/{documentId}`** — metadata only.
**`DELETE /api/v1/documents/{documentId}`** — soft delete + crypto-shred at retention expiry; audit entry written.

### 5.3 Extraction (asynchronous)

**`POST /api/v1/extractions`**
Scope: `ai:extract:<schemaId>`

```json
{
  "documentId": "doc_01J8ZK…",
  "schemaId": "invoice",
  "schemaVersion": 3,
  "options": {
    "reviewPolicy": "default",
    "locale": "en-IE",
    "hints": { "expectedVendorCountry": "IE" }
  },
  "callback": {
    "type": "KAFKA",
    "topic": "invoice.extraction.completed"
  }
}
```

Response `202`:

```json
{
  "jobId": "job_01J8ZL…",
  "status": "QUEUED",
  "statusUrl": "/api/v1/jobs/job_01J8ZL…",
  "estimatedCompletion": "2026-09-01T10:15:30Z"
}
```

Rules:
- `schemaVersion` is **mandatory**. There is no "latest".
- `hints` are advisory context only; they never override an extracted value.
- `callback.type` ∈ `KAFKA | SQS | NONE`. With `NONE`, consumers poll `statusUrl`.

**`GET /api/v1/jobs/{jobId}`**

```json
{
  "jobId": "job_01J8ZL…",
  "type": "EXTRACTION",
  "status": "COMPLETED",
  "submittedAt": "…",
  "completedAt": "…",
  "resultUrl": "/api/v1/extractions/job_01J8ZL…/result"
}
```

`status` ∈ `QUEUED | RUNNING | COMPLETED | NEEDS_REVIEW | FAILED | CANCELLED`.

**`GET /api/v1/extractions/{jobId}/result`**

```json
{
  "jobId": "job_01J8ZL…",
  "documentId": "doc_01J8ZK…",
  "status": "COMPLETED",
  "reviewStatus": "NOT_REQUIRED",
  "overallConfidence": 0.94,
  "fields": {
    "invoiceNumber": {
      "value": "INV-2026-000482",
      "type": "STRING",
      "confidence": 0.99,
      "source": { "page": 1, "bbox": [0.62, 0.08, 0.91, 0.11] },
      "reviewRequired": false
    },
    "invoiceDate": {
      "value": "2026-08-28",
      "type": "DATE",
      "confidence": 0.97,
      "source": { "page": 1, "bbox": [0.62, 0.12, 0.80, 0.15] },
      "reviewRequired": false
    },
    "totalAmount": {
      "value": { "amount": "12450.00", "currency": "EUR" },
      "type": "MONEY",
      "confidence": 0.81,
      "source": { "page": 2, "bbox": [0.70, 0.88, 0.95, 0.91] },
      "reviewRequired": true,
      "reviewReason": "BELOW_FIELD_THRESHOLD"
    },
    "lineItems": {
      "type": "ARRAY",
      "confidence": 0.88,
      "items": [
        {
          "description": { "value": "Advisory services Q2", "type": "STRING", "confidence": 0.93, "source": { "page": 1, "bbox": [0.08, 0.40, 0.55, 0.43] } },
          "quantity":    { "value": "1", "type": "DECIMAL", "confidence": 0.95, "source": { "page": 1, "bbox": [0.58, 0.40, 0.62, 0.43] } },
          "netAmount":   { "value": { "amount": "10000.00", "currency": "EUR" }, "type": "MONEY", "confidence": 0.90, "source": { "page": 1, "bbox": [0.80, 0.40, 0.95, 0.43] } }
        }
      ]
    },
    "iban": {
      "value": null,
      "type": "STRING",
      "confidence": 0.0,
      "reviewRequired": true,
      "reviewReason": "NOT_FOUND"
    }
  },
  "schemaValidation": {
    "valid": true,
    "violations": []
  },
  "warnings": [
    { "code": "LOW_IMAGE_QUALITY", "page": 2 }
  ],
  "provenance": { "…": "…" }
}
```

Contract guarantees:
- Every field declared in the schema is present in `fields`, with `value: null` and `confidence: 0` if not found. Consumers never null-check for absent keys.
- `type` is enforced by the service against the schema: a `MONEY` field is never a bare number or free text.
- `schemaValidation` reports structural violations (required field missing, regex mismatch, checksum failure such as IBAN mod-97). It does **not** apply business rules.
- When `reviewStatus` is `PENDING`, the result is available but flagged; consumers decide whether to proceed or wait for review completion.

### 5.4 Schemas

Schemas are the contract between the AI service and each consumer.

**`GET /api/v1/schemas/{schemaId}/versions/{version}`**

```json
{
  "schemaId": "identity_document",
  "version": 2,
  "status": "ACTIVE",
  "documentClass": "IDENTITY_DOCUMENT",
  "fields": [
    { "name": "documentType", "type": "ENUM", "values": ["PASSPORT", "NATIONAL_ID", "DRIVING_LICENCE"], "required": true },
    { "name": "documentNumber", "type": "STRING", "required": true, "pii": true },
    { "name": "surname", "type": "STRING", "required": true, "pii": true },
    { "name": "givenNames", "type": "STRING", "required": true, "pii": true },
    { "name": "dateOfBirth", "type": "DATE", "required": true, "pii": true },
    { "name": "nationality", "type": "COUNTRY", "required": true },
    { "name": "issuingCountry", "type": "COUNTRY", "required": true },
    { "name": "expiryDate", "type": "DATE", "required": true },
    { "name": "mrz", "type": "STRING", "required": false, "pii": true, "validators": ["MRZ_CHECKSUM"] }
  ],
  "reviewPolicy": {
    "fieldThreshold": 0.85,
    "overallThreshold": 0.90,
    "alwaysReview": ["documentNumber", "dateOfBirth"]
  },
  "createdAt": "…",
  "approvedBy": "arb-change-2026-041"
}
```

Field types: `STRING | DECIMAL | INTEGER | MONEY | DATE | DATETIME | BOOLEAN | ENUM | COUNTRY | ARRAY | OBJECT`.

Lifecycle: `DRAFT → ACTIVE → DEPRECATED → RETIRED`. A `DEPRECATED` version still serves requests but emits a `Deprecation` header with the sunset date. Schema creation/approval is a governed change, not a self-service API — managed through the registry admin interface with ARB sign-off.

### 5.5 Retrieval (synchronous)

**`POST /api/v1/retrieval/query`**
Scope: `ai:retrieve:<corpusId>`

```json
{
  "corpusId": "fund-policy-docs",
  "query": "What is the redemption notice period for the Alpha fund?",
  "topK": 8,
  "mode": "HYBRID",
  "filters": {
    "fundCode": ["ALPHA"],
    "effectiveDate": { "lte": "2026-09-01" }
  },
  "callerContext": {
    "subjectId": "u-48213",
    "entitlements": ["fund:ALPHA:read"]
  },
  "rerank": true
}
```

Response `200`:

```json
{
  "results": [
    {
      "chunkId": "chk_01J…",
      "sourceRef": { "corpusId": "fund-policy-docs", "documentId": "doc_…", "title": "Alpha Fund Prospectus 2026", "page": 14 },
      "text": "Redemptions require 30 calendar days' written notice…",
      "score": 0.87,
      "metadata": { "fundCode": "ALPHA", "effectiveDate": "2026-01-01" }
    }
  ],
  "provenance": { "embeddingModel": "text-embedding-3-large@2024-01", "rerankModel": "…", "auditId": "aud_…" }
}
```

Rules:
- `callerContext.entitlements` are **passed through as filters**, not evaluated by the service. The consumer remains the authorisation authority; the service guarantees only that chunks not matching the filter are never returned.
- `mode` ∈ `VECTOR | KEYWORD | HYBRID`.

**Corpus management** (`ai:corpus:write`):

- `POST /api/v1/corpora` — register a corpus `{ corpusId, owner, description, embeddingModel, chunking: { strategy, size, overlap }, metadataSchema }`.
- `POST /api/v1/corpora/{corpusId}/documents` — ingest for indexing (async job); accepts a `documentId` or a structured record payload for relational data.
- `DELETE /api/v1/corpora/{corpusId}/documents/{documentId}` — remove and reindex.

For the RAG assistant's relational source, the recommended pattern is a **projection job** owned by the RAG application that renders rows/records into text-with-metadata and pushes them into a corpus, rather than the AI service reading the relational database directly. This keeps schema knowledge and access control with the data owner.

### 5.6 Generation (synchronous)

Used by the RAG assistant for grounded answering, and available to other consumers for narrowly defined capabilities (e.g. summarising an extraction warning for a reviewer).

**`POST /api/v1/generate`**
Scope: `ai:generate:<capabilityId>`

```json
{
  "capabilityId": "rag.grounded_answer",
  "capabilityVersion": 4,
  "inputs": {
    "question": "What is the redemption notice period for the Alpha fund?",
    "context": [
      { "chunkId": "chk_01J…", "text": "Redemptions require 30 calendar days' written notice…", "sourceRef": { "title": "Alpha Fund Prospectus 2026", "page": 14 } }
    ],
    "conversation": [
      { "role": "user", "content": "…" },
      { "role": "assistant", "content": "…" }
    ]
  },
  "outputSchema": "rag.grounded_answer.v1",
  "callerContext": { "subjectId": "u-48213" }
}
```

Response `200`:

```json
{
  "output": {
    "answer": "The Alpha fund requires 30 calendar days' written notice for redemptions.",
    "citations": [ { "chunkId": "chk_01J…", "sourceRef": { "title": "Alpha Fund Prospectus 2026", "page": 14 } } ],
    "grounded": true,
    "abstained": false
  },
  "guardrails": {
    "inputPiiRedacted": false,
    "outputChecks": [ { "check": "GROUNDING", "result": "PASS" }, { "check": "TOXICITY", "result": "PASS" } ]
  },
  "usage": { "inputTokens": 1820, "outputTokens": 96 },
  "provenance": { "…": "…" }
}
```

Rules:
- `inputs` must match the capability's declared input schema; unknown keys → `400`. This is what stops prompt strings leaking across the boundary.
- `output` always conforms to `outputSchema` (enforced via structured output / JSON schema on the model call, then validated server-side). If the model cannot comply after bounded retries, the response is `422` with `problem.type = generation-schema-violation` — never a best-effort free-text fallback.
- `abstained: true` means the capability chose not to answer (insufficient grounding). Consumers must handle this explicitly.
- Streaming variant: `POST /api/v1/generate/stream` (SSE) for chat UX; final event carries the same `output`/`provenance` envelope.

### 5.7 Classification (synchronous, lightweight)

Small, cheap perception tasks that don't warrant a full extraction job — e.g. "which document class is this?" before routing, or "is this page a signature page?".

**`POST /api/v1/classify`**
Scope: `ai:classify:<capabilityId>`

```json
{ "capabilityId": "document.class", "documentId": "doc_…", "labels": ["INVOICE", "CREDIT_NOTE", "STATEMENT", "OTHER"] }
```

Response: `{ "label": "INVOICE", "confidence": 0.96, "alternatives": [ { "label": "CREDIT_NOTE", "confidence": 0.03 } ], "provenance": {…} }`

### 5.8 Human review

**`GET /api/v1/review/items?status=PENDING&schemaId=invoice&limit=50`**
Scope: `ai:review:read`

```json
{
  "items": [
    {
      "reviewItemId": "rev_01J…",
      "jobId": "job_01J8ZL…",
      "documentId": "doc_…",
      "schemaId": "invoice",
      "fields": ["totalAmount", "iban"],
      "reasons": ["BELOW_FIELD_THRESHOLD", "NOT_FOUND"],
      "createdAt": "…",
      "slaDueAt": "…"
    }
  ],
  "nextCursor": "…"
}
```

**`POST /api/v1/review/items/{reviewItemId}/decision`**
Scope: `ai:review:write`

```json
{
  "decisions": {
    "totalAmount": { "action": "CONFIRM" },
    "iban": { "action": "CORRECT", "value": "IE29AIBK93115212345678" }
  },
  "reviewer": { "subjectId": "u-1029" },
  "comment": "IBAN present in footer, low contrast scan."
}
```

`action` ∈ `CONFIRM | CORRECT | REJECT`. On completion the job transitions to `COMPLETED` (or `FAILED` on reject), the result is updated with `reviewStatus: COMPLETED` and per-field `reviewedBy`, the completion event is emitted, and the correction is captured as a labelled example for the evaluation dataset (subject to data-handling approval).

Consumers may build their own review UI on this API, or use the service's reference UI. Either way there is a single review data model.

### 5.9 Registry (read-only for consumers)

- `GET /api/v1/capabilities` — list capabilities the caller is entitled to, with current versions.
- `GET /api/v1/capabilities/{capabilityId}/versions/{v}` — declared input/output schema, model binding, guardrail policy, status.
- `GET /api/v1/models` — available model bindings with status (`ACTIVE | DEPRECATED | DISABLED`).

Write operations on the registry are administrative and go through change control, not through consumer clients.

### 5.10 Asynchronous events

Emitted to Kafka (or SQS) with the same envelope; consumers subscribe to their own topics only.

```json
{
  "eventId": "evt_01J…",
  "eventType": "ai.extraction.completed",
  "occurredAt": "…",
  "correlationId": "9f1c…",
  "callerId": "invoice-pipeline",
  "payload": {
    "jobId": "job_01J8ZL…",
    "documentId": "doc_…",
    "status": "COMPLETED",
    "reviewStatus": "NOT_REQUIRED",
    "resultUrl": "/api/v1/extractions/job_01J8ZL…/result"
  }
}
```

Event types: `ai.extraction.completed`, `ai.extraction.needs_review`, `ai.extraction.failed`, `ai.review.completed`, `ai.corpus.index.completed`, `ai.capability.deprecated`.

Events carry references, not payloads: results are fetched via the API so that access control and audit apply uniformly.

### 5.11 Scope matrix

| Client | Scopes |
|---|---|
| rag-assistant | `ai:retrieve:fund-policy-docs`, `ai:generate:rag.grounded_answer`, `ai:corpus:write` (own corpora only) |
| kyc-pipeline | `ai:documents:write` (class `IDENTITY_DOCUMENT`), `ai:extract:identity_document`, `ai:classify:document.class`, `ai:review:read/write` (own items) |
| invoice-pipeline | `ai:documents:write` (class `INVOICE`), `ai:extract:invoice`, `ai:classify:document.class`, `ai:review:read/write` (own items) |

Scopes are enforced at the resource level too: a client can only read jobs, documents, and review items it created.

---

## 6. Governance and controls

| Control | Implementation |
|---|---|
| **Audit log** | Append-only table; one row per model call: `auditId, correlationId, callerId, subjectId, capabilityId/version, schemaId/version, modelId, promptVersion, inputHash, outputHash, tokens, latency, guardrailResults, timestamp`. Raw inputs/outputs stored in object storage under retention policy, referenced by hash. |
| **PII handling** | Ingest-time PII inventory per document; redaction applied before any call to an external provider unless the capability is explicitly approved for PII (KYC extraction is; RAG generation over policy documents is not). |
| **Model change control** | New model binding = new capability version; requires evaluation run against the capability's golden set with results attached to the change record; blue/green routing with percentage rollout; one-click rollback. |
| **Evaluation harness** | Golden datasets per capability (extraction: labelled documents; RAG: question/answer/citation sets). Metrics: field-level accuracy, F1, grounding rate, abstention correctness, latency, cost. Run on every registry change and nightly. |
| **Kill switch** | Per capability and per provider; flips to `DISABLED`, requests return `503` with `Retry-After`, async jobs remain queued. |
| **Cost control** | Token budgets per caller per day; soft alert at 80%, hard stop at 100% for non-critical capabilities. Cost attributed on the audit row. |
| **Data residency** | Provider adapters carry a residency attribute; capabilities declare permitted regions; router refuses non-compliant combinations. |
| **Vendor exit** | Provider adapter interface; no consumer references a vendor. Prompts stored in vendor-neutral form with per-provider rendering. |

---

## 7. Non-functional requirements

| Area | Target |
|---|---|
| Availability | Gateway 99.9%; extraction worker 99.5% with queue-backed durability (no work loss at any availability level). |
| Latency | Retrieval p95 < 300 ms; grounded generation p95 < 6 s (non-streaming); extraction job completion p95 < 90 s for ≤ 5 pages. |
| Throughput | Extraction: 5,000 documents/day initial, horizontally scalable via worker replicas. |
| Idempotency | All work-creating POSTs; 24-hour key retention. |
| Retention | Documents and raw model I/O per document-class policy (KYC: per regulatory requirement; invoices: 7 years); audit metadata indefinitely. |
| Observability | OpenTelemetry traces across gateway → worker → provider; metrics per capability (calls, errors, latency, tokens, cost, review rate); structured logs with correlation ID. |
| Security | mTLS between services; OAuth2 client credentials with short-lived tokens; secrets in vault; per-caller KMS keys; no model I/O in application logs. |

---

## 8. Migration plan

| Phase | Scope | Exit criteria |
|---|---|---|
| **0 — Foundation** (4–6 wks) | Gateway, registry, audit, provider adapters, document ingestion, job framework. No consumers yet. | Audit and evaluation harness demonstrated to Risk; ARB approval of contracts. |
| **1 — Invoice extraction** (4 wks) | Wrap IDP behind `extract.invoice` v1; invoice pipeline switches to async jobs; rule engine unchanged, consuming typed result. Shadow mode first (both paths run, outputs compared). | Field-level parity ≥ existing IDP on golden set; review queue live. |
| **2 — KYC extraction** (3 wks) | `extract.identity_document` v1; KYC pipeline switches; review policy with mandatory review fields. | Parity on golden set; PII controls signed off. |
| **3 — RAG** (4–6 wks) | Corpus registration, projection job from relational source, retrieval, `rag.grounded_answer`. | Grounding rate and citation accuracy meet agreed thresholds; entitlement pass-through verified. |
| **4 — Consolidation** | Decommission per-app model access; central cost reporting; quarterly model review cadence. | No direct provider credentials outside the service. |

Start with invoices rather than KYC because the data classification is lower and the rule engine provides an existing, deterministic oracle for parity testing.

---

## 9. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Shared service becomes a delivery bottleneck | Published SLA; schema-change process with fixed lead times; consumer teams own their schemas and review policies; service team owns the platform, not the content. |
| Scope creep into workflow/business logic | Charter in this document; ARB gate on any capability whose input or output encodes a business decision. |
| Model regression after upgrade | Mandatory evaluation gate; blue/green rollout; per-capability rollback. |
| Provider outage | Multi-provider bindings for critical capabilities; queue-backed async; kill switch. |
| Data leakage across consumers | Per-caller isolation at storage, corpus, key, and scope level; tested in CI with negative authorisation tests. |
| Over-reliance on confidence scores | Confidence calibrated per schema against golden sets; mandatory-review fields for high-impact data regardless of confidence. |

---

## 10. Open decisions

1. Vector store: pgvector (simplicity, single operational footprint) vs dedicated store (scale). Recommendation: pgvector until corpus size or latency forces a change.
2. Whether the reference review UI is built by the platform team or each consumer integrates the review API into its existing case tooling.
3. Retention of raw model inputs/outputs for RAG conversations — required for audit vs data-minimisation obligations. Needs Compliance input.
4. Whether IDP is replaced by LLM-based extraction in a later phase; the contract is designed so this is invisible to consumers.

---

## Appendix A — Field and job state machines

**Job**: `QUEUED → RUNNING → (COMPLETED | NEEDS_REVIEW | FAILED)`; `NEEDS_REVIEW → (COMPLETED | FAILED)` on review decision; any non-terminal → `CANCELLED` via `DELETE /api/v1/jobs/{jobId}`.

**Review item**: `PENDING → (RESOLVED | REJECTED | EXPIRED)`.

**Capability / schema version**: `DRAFT → ACTIVE → DEPRECATED → RETIRED`.

## Appendix B — Reason and warning codes

Review reasons: `BELOW_FIELD_THRESHOLD`, `BELOW_OVERALL_THRESHOLD`, `NOT_FOUND`, `MANDATORY_REVIEW_FIELD`, `VALIDATOR_FAILED`, `CONFLICTING_CANDIDATES`.

Warnings: `LOW_IMAGE_QUALITY`, `ROTATED_PAGE`, `MULTIPLE_DOCUMENTS_DETECTED`, `LANGUAGE_MISMATCH`, `PARTIAL_PAGE`.
