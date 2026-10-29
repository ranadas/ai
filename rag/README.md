# RAG: Documents + SQL Database

A retrieval-augmented generation app that answers natural-language questions using
two sources of truth: a cache of local documents (PDF, DOCX, CSV/XLSX) and a SQL
database. Claude decides at runtime which source(s) a question needs and calls
tools to fetch the data before answering.

## Architecture

```
data/documents/  --loaders-->  chunks  --TF-IDF-->  InMemoryVectorStore (search_documents tool)
SQL database (in-memory SQLite by default, or any SQLAlchemy URL) --> (query_database tool)
                                        |
                                        v
                        RAGPipeline (tool-use agent loop) --> Claude --> answer
```

- **Ingestion** (`ragapp/ingestion/loaders.py`): extracts text from `.pdf`, `.docx`,
  `.csv`, `.xlsx`/`.xls` files.
- **Indexing** (`ragapp/chunking.py`, `ragapp/vector_store.py`): chunks document text
  and builds an in-memory TF-IDF index (`scikit-learn`) - no external vector database
  required. Swap in real embeddings (e.g. Voyage AI, sentence-transformers) by
  reimplementing `InMemoryVectorStore` behind the same `build`/`search` interface.
- **SQL** (`ragapp/sql_store.py`): connects via SQLAlchemy. Defaults to an in-memory
  SQLite database seeded with sample `customers`/`orders` tables; point
  `RAG_SQL_CONNECTION_STRING` at a real database to use your own schema instead.
- **Generation** (`ragapp/pipeline.py`): a tool-use agent loop against the OpenAI API.
  The model is given `search_documents` and `query_database` function tools plus the
  live DB schema, and decides which to call - one, both, or neither - before writing
  a final, source-cited answer.

## Setup

```bash
pip install -r requirements.txt
cp .env.example .env   # then fill in OPENAI_API_KEY
python scripts/generate_sample_data.py   # optional: populate data/documents/ with samples
```

## Usage

```bash
# Inspect what would be indexed from the document cache
python cli.py index

# One-shot question
python cli.py ask "What is the refund policy for the Enterprise Plan, and how many orders has Dev Patel placed?"

# Interactive session (indexes once, then answers repeated questions)
python cli.py chat
```

Point at a different document cache with `--docs-dir path/to/files`.

## Configuration (env vars)

| Variable | Default | Purpose |
|---|---|---|
| `OPENAI_API_KEY` | - | OpenAI API credential |
| `RAG_MODEL` | `gpt-5` | Model used for answer generation (e.g. `gpt-5`, `gpt-5-mini`, `gpt-4.1`) |
| `RAG_DOCS_DIR` | `data/documents` | Document cache directory |
| `RAG_SQL_CONNECTION_STRING` | `sqlite:///:memory:` | SQLAlchemy connection string |
| `RAG_CHUNK_SIZE` / `RAG_CHUNK_OVERLAP` | `1000` / `150` | Chunking parameters |

## Connecting a real database

Set `RAG_SQL_CONNECTION_STRING` to any SQLAlchemy URL, e.g.
`postgresql+psycopg2://user:pass@host:5432/dbname` or `mysql+pymysql://...`, and
install the matching driver. Demo seeding only runs for the default in-memory
SQLite URL, so a real database is used as-is.

## Security note

`query_database` only accepts single, read-only `SELECT`/`WITH` statements - it
rejects multiple statements and DML/DDL keywords. This is a safety net around
LLM-generated SQL, not a substitute for connecting through a read-only database
role in production.

## Note on LLM provider

This app calls the OpenAI API (`openai` SDK, `chat.completions` with function
calling) via `ragapp/pipeline.py`. To switch providers, that file and
`ragapp/tools.py` (tool schema format) are the only places that are provider-specific
- `ingestion`, `chunking`, `vector_store`, and `sql_store` are unaffected.

## Steps to run it
- 1. Set up the environment (one-time)
cd /Users/ranadas/Workspace/ai-folder/rag
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

- 2. Add your API key
cp .env.example .env
Then edit .env and set ANTHROPIC_API_KEY=sk-ant-... (or if you use ant auth login instead, you can skip this — the SDK picks up that credential automatically).

- 3. (Optional) Regenerate sample documents — already done, but if you want fresh ones:
python scripts/generate_sample_data.py

- 4. Ask questions

One-shot:
```python
python cli.py ask "What is the refund policy for the Enterprise Plan, and how many orders has Dev Patel placed?"
```
This question deliberately needs both sources — the refund policy lives in company_policy.docx, and Dev Patel's order count lives in the seeded in-memory orders table. Claude will call search_documents for the first part and query_database for the second, then combine them into one answer citing both.

Interactive session (builds the index once, then answers repeatedly — better for multiple questions):
```bash
python cli.py chat
> What plans do you offer, and which region has the most customers?
> Which customers are on the Enterprise Plan and what's their support SLA?
> exit
```
Other useful command
```bash
python cli.py index          # just shows what would be indexed, no API call
python cli.py chat --docs-dir /path/to/your/own/files   # point at your own document cache
```


To use a real SQL database instead of the demo in-memory one, set RAG_SQL_CONNECTION_STRING in .env (e.g. postgresql+psycopg2://user:pass@host:5432/dbname) and install the matching driver — everything else stays the same.
