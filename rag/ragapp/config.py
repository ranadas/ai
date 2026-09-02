import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()

DOCS_DIR = Path(os.environ.get("RAG_DOCS_DIR", "data/documents"))
MODEL = os.environ.get("RAG_MODEL", "claude-opus-5")
SQL_CONNECTION_STRING = os.environ.get("RAG_SQL_CONNECTION_STRING", "sqlite:///:memory:")
CHUNK_SIZE = int(os.environ.get("RAG_CHUNK_SIZE", "1000"))
CHUNK_OVERLAP = int(os.environ.get("RAG_CHUNK_OVERLAP", "150"))
TOP_K = int(os.environ.get("RAG_TOP_K", "5"))
