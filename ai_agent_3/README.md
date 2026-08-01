# Invoice Agent

Spring Boot 4 invoice processing service using Spring AI OpenAI integration and Koog agents.

## Flow

1. Accept an invoice PDF through `POST /api/invoices/process`.
2. Extract PDF text with PDFBox.
3. Run a Koog extraction agent to identify invoice values.
4. Run a separate Koog four-eye review agent.
5. Push approved values to Geneva over HTTP.
6. Create a NAV control pack JSON file.
7. Notify the client callback with `CONFIRM` or `REJECT`.
8. Persist process state and audit events in a relational database.

## Configuration

Key environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `OPENAI_API_KEY` | empty | OpenAI API key. |
| `OPENAI_BASE_URL` | `https://api.openai.com` | OpenAI or OpenAI-compatible base URL. |
| `OPENAI_MODEL` | `gpt-5-mini` | Spring AI default OpenAI chat model. |
| `DATABASE_URL` | H2 in-memory | JDBC URL. Use PostgreSQL in non-local environments. |
| `GENEVA_BASE_URL` | `http://localhost:8081` | Geneva API host. |
| `GENEVA_DRY_RUN` | `true` | Skip Geneva HTTP calls when true. |
| `CLIENT_CALLBACK_URL` | empty | Default client callback URL. |
| `CLIENT_CALLBACK_DRY_RUN` | `true` | Skip client callback HTTP calls when true. |
| `NAV_CONTROL_PACK_DIR` | `build/nav-control-packs` | Local NAV control pack output directory. |

## Run

```bash
export OPENAI_API_KEY=...
export OPENAI_BASE_URL=https://api.openai.com
mvn spring-boot:run
```

## Process an invoice

```bash
curl -F clientId=client-123 \
  -F invoiceReference=INV-REF-001 \
  -F callbackUrl=https://client.example.com/payment-decisions \
  -F invoice=@sample-invoice.pdf \
  http://localhost:8080/api/invoices/process
```

## Check status

```bash
curl http://localhost:8080/api/invoices/{processId}
```

## Production notes

- Replace H2 with PostgreSQL by setting `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and `DATABASE_DRIVER=org.postgresql.Driver`.
- Keep `GENEVA_DRY_RUN=false` and `CLIENT_CALLBACK_DRY_RUN=false` only when downstream APIs are reachable and authenticated.
- Add service-to-service authentication for Geneva and callback APIs before production use.
- Add human review queue integration for `NEEDS_MANUAL_REVIEW` outcomes.
