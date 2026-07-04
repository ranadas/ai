# AI Agent-Based Invoice Processing System

Spring Boot **4.0.7** + **Java 21** + Koog **1.0.0** + Spring AI **2.0.0** scaffold for automating a manual invoice-processing workflow.

The design follows the uploaded reference architecture in `docs/reference-orchestration.png`:

1. **Invoice Ingest & Understanding Agent** extracts PDF/manual invoice elements using an OpenAI-compatible LLM endpoint.
2. **Data Validation & Enrichment Agent** validates mandatory fields and enriches with master-data-style checks.
3. **Four Eye Review Agent** decides whether straight-through processing is allowed or human review is required.
4. **Post & Integration Agent** pushes approved invoice values to Geneva via HTTP API.
5. **NAV Control Pack & Notification Agents** generate a NAV control pack and send success/failure response to the client.

## What changed from the previous Kotlin version

- Removed Kotlin Gradle plugins and Kotlin source sets.
- Replaced Kotlin data classes with Java 21 records.
- Replaced Kotlin Spring components with Java 21 classes.
- Kept the same multi-agent flow, REST contract, OpenAI-compatible configuration, Geneva API client, NAV control pack generation, and client callback notification.
- Kept `ai.koog:koog-agents:1.0.0` as the requested agent-framework dependency and exposed a `KoogAgentRuntime` seam for production Koog-backed agent implementations.

## REST endpoints

### Process JSON invoice

```bash
curl -X POST http://localhost:8080/api/invoices/process \
  -H 'Content-Type: application/json' \
  -d '{
    "manualText": "Invoice Number: INV-1001\nVendor: Acme Fund Services\nCurrency: USD\nTotal: 1250.00",
    "clientCallbackUrl": "http://localhost:9092"
  }'
```

### Process PDF invoice

```bash
curl -X POST http://localhost:8080/api/invoices/process \
  -F 'file=@invoice.pdf' \
  -F 'clientCallbackUrl=http://localhost:9092'
```

### Retrieve processing state

```bash
curl http://localhost:8080/api/invoices/{invoiceId}
```

## Configuration

All OpenAI-compatible settings are externally configurable, including base URL:

```yaml
invoice:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      model: ${OPENAI_MODEL:gpt-4o-mini}

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      chat:
        options:
          model: ${OPENAI_MODEL:gpt-4o-mini}
```

Geneva and client callback APIs are also configurable:

```yaml
invoice:
  geneva:
    base-url: ${GENEVA_BASE_URL}
    api-key: ${GENEVA_API_KEY}
  client-callback:
    enabled: ${CLIENT_CALLBACK_ENABLED:false}
    base-url: ${CLIENT_CALLBACK_BASE_URL}
    api-key: ${CLIENT_CALLBACK_API_KEY}
```

## Running locally

```bash
./gradlew bootRun
```

For local dry runs with no LLM key, the app uses deterministic fallback extraction from manual text and skips client callback unless explicitly enabled.

## Java 21 layout

```text
src/main/java/com/example/invoice
├── api                  REST controller
├── agent                Agent contract, orchestration, and processing agents
├── config               Configuration properties and Java HttpClient bean
├── domain               Java records/enums for invoice state and results
├── integrations         OpenAI-compatible, Geneva, and client-callback HTTP clients
└── store                In-memory invoice result store
```

## Koog usage note

`ai.koog:koog-agents:1.0.0` is included in the build. The Java app is split into small `InvoiceAgent<I, O>` units so each step can be replaced by a concrete Koog tool-enabled agent with tools, memory, and observability. `KoogAgentRuntime` is the intended factory/registry seam for production Koog agents.

## Suggested next production hardening

- Replace in-memory store with Postgres or MongoDB.
- Add actual PDF OCR/parsing, for example Apache PDFBox/Tesseract or a document AI service.
- Add human review workbench persistence and reviewer identity capture.
- Add Geneva API contract DTOs and retries with idempotency keys.
- Add audit retention, metrics, tracing, and security.
