from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity


@dataclass
class DocChunk:
    id: str
    source: str
    text: str


class InMemoryVectorStore:
    """TF-IDF backed in-memory search index (no external vector database)."""

    def __init__(self):
        self._vectorizer = TfidfVectorizer(stop_words="english")
        self._matrix = None
        self._chunks: list[DocChunk] = []

    def build(self, chunks: list[DocChunk]) -> None:
        self._chunks = chunks
        texts = [c.text for c in chunks]
        self._matrix = self._vectorizer.fit_transform(texts) if texts else None

    @property
    def size(self) -> int:
        return len(self._chunks)

    def search(self, query: str, top_k: int = 5) -> list[tuple[DocChunk, float]]:
        if self._matrix is None or self._matrix.shape[0] == 0:
            return []
        query_vec = self._vectorizer.transform([query])
        scores = cosine_similarity(query_vec, self._matrix)[0]
        ranked = np.argsort(scores)[::-1][:top_k]
        return [(self._chunks[i], float(scores[i])) for i in ranked if scores[i] > 0]
