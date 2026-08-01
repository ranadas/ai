# ID Intake Agent

Monitors a folder for photos of identity documents (passports, national IDs,
driving licences). When a new image arrives it is sent to an OpenAI vision
model, the data points are extracted and written as a CSV into a folder named
after the person on the document.

## Folder layout (created automatically)

```
incoming/                  <- drop ID photos here
extracted/
  .processed_ledger.json   <- ledger of already-processed files
  JOHN_DOE/
    passport_20260612_141700.csv
    passport.jpg           <- original image, archived after processing
rejected/                  <- images the model says are not ID documents
```

## Setup

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # then put your real OPENAI_API_KEY in .env
```

Keys are read from `.env` in the project root (loaded via `python-dotenv`).
`.env` is gitignored; `.env.example` documents the expected variables.
Optional: set `OPENAI_MODEL` in `.env` to change the default model.

## Run

```bash
python id_agent.py
# or with options:
python id_agent.py --inbox incoming --output extracted --model gpt-4o --skip-existing
```

`--skip-existing` marks files already sitting in the inbox as processed
without sending them, so only future arrivals are extracted.

## Guardrails

- **Only new files are sent.** Every processed file's SHA-256 is stored in
  `extracted/.processed_ledger.json`. Re-dropped or renamed copies of the same
  image are skipped, and the ledger survives restarts.
- **No assumed values.** Every extraction field is nullable and the prompt
  forbids inferring, normalising or guessing. Unreadable fields come back as
  `null` and are written as empty CSV cells; the log lists which fields were
  left empty for each document.
- **Non-ID images are not extracted** — they are moved to `rejected/` and
  ledgered so they are not retried.
- Files are only read after their size is stable, so half-copied uploads are
  never sent. Failed API calls are retried on the next rescan (max 3 attempts
  per file per run).

## CSV format

One header row + one data row per document:

```
source_file,document_type,issuing_country,issuing_authority,full_name,surname,given_names,document_number,personal_number,nationality,date_of_birth,place_of_birth,sex,date_of_issue,date_of_expiry,address,mrz_line_1,mrz_line_2
```

> **Privacy note:** images are sent to the OpenAI API and CSVs contain
> personal data. Make sure this complies with your data-processing agreements
> (GDPR) before running on real documents.
