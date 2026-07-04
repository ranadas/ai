# invoice-agent

AI agent–based invoice processing service.
**Spring Boot 4.0.7 · Kotlin 2.3 · Koog 1.0.0 · Spring AI 2.0.0 (OpenAI-compatible, configurable base URL)**

Automates a manual invoice workflow: LLM extraction of PDF invoice elements,
validation & enrichment against master data, a mandatory **four-eye human
review gate**, posting of validated values to **Geneva** via REST, generation
of an audit-ready **NAV control pack**, and a final **success/failure response
to the client** confirming or rejecting the payment.

## Architecture

```
POST /invoices (PDF, 202)                POST /invoices/{id}/review
        │                                          │  (human, four-eye)
        ▼                                          ▼
┌────────────────┐   ┌──────────────────┐   ┌────────────┐   ┌──────────────────────────────┐
│ 1. Extraction  │ → │ 2. Validation &  │ → │ 3. PENDING │ → │ 4+5. Posting agent           │
│    agent       │   │    enrichment    │   │    REVIEW  │   │  pushInvoiceToGeneva         │
│  (Koog, no     │   │    agent (Koog + │   │  (humans   │   │  createNavControlPack        │
│   tools)       │   │    tools)        │   │   only)    │   │  notifyClient (SUCCESS/FAIL) │
└────────────────┘   └──────────────────┘   └────────────┘   └──────────────────────────────┘
```

Two deliberate design decisions for a regulated environment:

1. **Deterministic control flow, agentic stages.** The *sequence* of the
   pipeline is plain Kotlin (`InvoiceProcessingService`); Koog agents reason
   only *within* a stage (which vendor to look up, how to summarise findings,
   how to react to a failed Geneva push). Prompts never decide whether an
   invoice may be paid.
2. **Guards in code, not prompts.** Segregation of duties (`ReviewService`),
   the approved-before-posting check and the "no SUCCESS notification without
   a Geneva reference" check (`PostingTools`) are enforced in Kotlin. Tools
   accept only the invoice **id** — the LLM never re-types monetary amounts,
   so a hallucinated figure cannot reach the rule engine or Geneva.

Other properties: idempotent submission (`Idempotency-Key`), idempotent Geneva
push (same key forwarded downstream), full who-did-what-when audit trail
including every agent tool call, and RFC 7807 problem details.

## LLM connectivity (configurable base URL)

Spring AI owns the OpenAI-compatible transport; Koog's
`koog-spring-ai-starter-model-chat` bridge wraps the auto-configured
`ChatModel` into the Koog `PromptExecutor` the agents run on. Point it at
OpenAI, Azure OpenAI, a corporate LLM gateway, or a local vLLM/Ollama endpoint:

```bash
export OPENAI_API_KEY=sk-...
export OPENAI_BASE_URL=https://api.openai.com     # or your gateway
export OPENAI_MODEL=gpt-4o
```

(Alternative: drop the Spring AI bridge and use Koog's own starter —
`ai.koog.openai.api-key` / `ai.koog.openai.base-url` — the agent code is
identical either way.)

## Run

```bash
./gradlew bootRun
```

The Geneva endpoint and client notification endpoint default to localhost
stubs (`GENEVA_BASE_URL`, `CLIENT_NOTIFY_URL`) — point WireMock at 9090/9091
for local end-to-end runs.

### Walkthrough

```bash
# 1. Submit an invoice PDF (async 202 + Location header)
curl -i -X POST http://localhost:8080/api/v1/invoices \
  -H "Idempotency-Key: inv-2026-07-001" \
  -H "X-Submitted-By: alice" \
  -F "file=@sample-invoice.pdf"

# 2. Poll status — extraction + validation run in the background,
#    then the invoice lands in PENDING_REVIEW with an agent-written summary
curl http://localhost:8080/api/v1/invoices/{id}

# 3. Four-eye review (a different user; self-review returns 403)
curl -X POST http://localhost:8080/api/v1/invoices/{id}/review \
  -H "Content-Type: application/json" \
  -d '{"reviewer": "bob", "decision": "APPROVE", "comment": "Matches PO-88801"}'

# 4. On approval the posting agent pushes to Geneva, builds the control pack
#    and notifies the client. Inspect the results:
curl http://localhost:8080/api/v1/invoices/{id}/audit
curl http://localhost:8080/api/v1/invoices/{id}/control-pack
```

## Layout

| Package | Responsibility |
|---|---|
| `agent` | Koog agent factory + system prompts |
| `agent.tools` | Koog `ToolSet`s (validation lookups, Geneva push, control pack, client notification) — all state-guarded |
| `service` | Deterministic orchestration, four-eye review, NAV control pack |
| `integration` | `RestClient`-based Geneva + client notification HTTP clients (timeouts, idempotency key) |
| `rules` | Deterministic business rule engine + vendor/PO master stubs |
| `extraction` | PDFBox text extraction (OCR pluggable behind the same interface) |
| `store`, `audit` | In-memory store + end-to-end audit trail |
| `api` | REST controller, DTOs, RFC 7807 error handling |

## Productionisation notes

- Swap `InMemoryInvoiceStore` for Spring Data JPA (Postgres + Flyway); the
  interface is the seam. Store PDFs in object storage, not the row.
- `AuditTrail.record` should additionally publish to a Kafka `invoice.audit`
  topic; the current implementation is the single point to extend.
- Replace the `X-Submitted-By` header with the OAuth2 principal
  (deny-by-default resource server) and drive reviewer identity from the token.
- Client notification failures belong on a retry queue (Kafka/SQS + DLQ).
- Wrap `GenevaClient` in Resilience4j (circuit breaker + retry with the same
  idempotency key).
- Track **straight-through-processing rate** (invoices reaching
  PENDING_REVIEW with zero findings) and review turnaround as the primary
  KPIs; Koog's OpenTelemetry feature plugs agent token/latency metrics into
  the same Prometheus/Grafana stack.
- Koog note: tool sets use the reflection-based `@Tool` / `@LLMDescription`
  API from the stable 1.0 line. If your build resolves different symbol
  locations, check the Koog 1.0 migration notes — 1.0 removed all previously
  deprecated APIs.
