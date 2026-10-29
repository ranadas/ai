# Vision Agent

A Spring Boot AI agent that accepts an **image or a PDF** over HTTP, sends it to
a **vision-capable LLM**, and returns the **data points** found in the document
as structured JSON. PDFs are rendered page-by-page to images before being sent
to the model, so both digital and scanned PDFs work.

It runs against a **local Ollama** model out of the box, and can be switched to
**OpenAI** later with only a build flag and configuration change — the
application code never changes, because it depends on Spring AI's
provider-neutral `ChatClient`.

## Requirements

- Java 21
- Maven 3.9+
- [Ollama](https://ollama.com) running locally with a vision model:
  ```bash
  ollama pull llava        # or: ollama pull llama3.2-vision
  ollama serve             # usually already running
  ```

## Run (default: local Ollama)

```bash
mvn spring-boot:run
```

Override the model or endpoint via environment variables:

```bash
OLLAMA_MODEL=llama3.2-vision OLLAMA_BASE_URL=http://localhost:11434 mvn spring-boot:run
```

## Call the API

```bash
# An image
curl -F "file=@/path/to/receipt.png" \
     -F "instructions=Focus on totals, tax and date" \
     http://localhost:8080/api/v1/extract

# A PDF (rendered to page images, then analysed)
curl -F "file=@/path/to/invoice.pdf" \
     -F "instructions=Extract line items and the grand total" \
     http://localhost:8080/api/v1/extract
```

Example response:

```json
{
  "summary": "A grocery store receipt.",
  "documentType": "receipt",
  "dataPoints": [
    { "label": "Store",  "value": "ACME Market", "confidence": 0.97 },
    { "label": "Total",  "value": "12.34",        "confidence": 0.95 },
    { "label": "Date",   "value": "2026-06-21",   "confidence": 0.90 }
  ]
}
```

| Field          | Type   | Required | Notes                                                  |
|----------------|--------|----------|--------------------------------------------------------|
| `file`         | file   | yes      | multipart image (`image/*`) or PDF (`application/pdf`) |
| `image`        | file   | no       | legacy alias for `file` (image only)                   |
| `instructions` | string | no       | extra task-specific guidance for the model             |

### PDF rendering tunables

| Property                          | Env var         | Default | Meaning                                  |
|-----------------------------------|-----------------|---------|------------------------------------------|
| `vision-agent.document.pdf-dpi`   | `PDF_DPI`       | 150     | resolution PDF pages are rendered at     |
| `vision-agent.document.max-pages` | `PDF_MAX_PAGES` | 10      | max PDF pages rendered and sent to model |

Health check: `GET http://localhost:8080/actuator/health`

## Switch to OpenAI (future)

No Java changes are needed. Build with the `openai` Maven profile (which adds the
OpenAI starter) and activate the `openai` Spring profile:

```bash
mvn -Popenai clean package

SPRING_PROFILES_ACTIVE=openai \
OPENAI_API_KEY=sk-... \
OPENAI_MODEL=gpt-4o \
java -jar target/vision-agent-0.1.0-SNAPSHOT.jar
```

## How it works

```
HTTP multipart (image or PDF)
        │
        ▼
ExtractionController  ──►  DocumentToImagesConverter  ──►  VisionExtractionService  ──►  Spring AI ChatClient
                           (PDF → page images via                                            │
                            Apache PDFBox)                          ┌──────────────────────┴───────────────────┐
                                                                    ▼                                            ▼
                                                             Ollama (default)                         OpenAI (profile: openai)
```

- `ExtractionController` validates the upload and exposes `POST /api/v1/extract`.
- `DocumentToImagesConverter` passes images through unchanged and renders PDF
  pages to PNG images with Apache PDFBox (works for scanned PDFs too).
- `VisionExtractionService` builds one multimodal prompt (text + all page
  images) and asks the model to return JSON matching `ExtractionResult`. Spring
  AI generates the JSON schema from the record and deserializes the response.
- The provider is chosen purely by dependencies + config.

## Test

```bash
mvn test
```

The web-layer tests mock the LLM, so they run offline (no Ollama needed).
