# Agent Watcher

Java 21 / Spring Boot 4.0.3 migration of the Python `id_agent.py` folder watcher.

It monitors an inbox for ID document images, sends each new image to an OpenAI vision model, writes the extracted fields to CSV, archives accepted images under a person-specific folder, and moves non-ID images to `rejected/`.

## Run

Create `.env` in this folder, or export the variables before starting:

```bash
OPENAI_API_KEY=...
OPENAI_MODEL=gpt-4o
```

Then run:

```bash
mvn spring-boot:run
```

Options match the Python script:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--inbox incoming --output extracted --rejected rejected --model gpt-4o --skip-existing"
```

## CSV fields

```text
source_file,document_type,issuing_country,issuing_authority,full_name,surname,given_names,document_number,personal_number,nationality,date_of_birth,place_of_birth,sex,date_of_issue,date_of_expiry,address,mrz_line_1,mrz_line_2
```

## Guardrails Preserved

- SHA-256 ledger at `extracted/.processed_ledger.json` prevents duplicate content from being resent.
- Files are processed only after their size is stable.
- Extraction uses a strict JSON schema where every document field is nullable.
- Non-ID images are moved to `rejected/` and recorded in the ledger.
- API failures are retried on later events or rescans, up to three attempts per file per run.
