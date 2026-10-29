from __future__ import annotations

from pathlib import Path

from .chunking import chunk_text
from .ingestion.loaders import RawDocument, load_documents
from .vector_store import DocChunk, InMemoryVectorStore


def build_index(
    docs_dir: Path, chunk_size: int = 1000, overlap: int = 150
) -> tuple[InMemoryVectorStore, list[RawDocument]]:
    raw_docs = load_documents(docs_dir)
    chunks: list[DocChunk] = []
    for doc in raw_docs:
        for i, piece in enumerate(chunk_text(doc.text, chunk_size, overlap)):
            chunks.append(DocChunk(id=f"{doc.source}::{i}", source=doc.source, text=piece))
    store = InMemoryVectorStore()
    store.build(chunks)
    return store, raw_docs
