"""Token-aware chunking with overlap. Tokens are approximated by whitespace words to avoid a
tokenizer dependency (the embedder truncates internally anyway). Each chunk carries the document
title as a prefix so a standalone chunk stays self-describing for retrieval."""
from __future__ import annotations

from .schema import Chunk, Config, Document, stable_chunk_id


def _split_words(text: str) -> list[str]:
    return text.split()


def chunk_document(doc: Document, cfg: Config) -> list[Chunk]:
    words = _split_words(doc.body)
    if not words:
        return []
    size = max(1, cfg.max_tokens)
    overlap = max(0, min(cfg.overlap_tokens, size - 1))
    step = size - overlap

    chunks: list[Chunk] = []
    ordinal = 0
    for start in range(0, len(words), step):
        window = words[start:start + size]
        if not window:
            break
        body = " ".join(window)
        text = f"{doc.title}: {body}" if doc.title else body
        chunks.append(
            Chunk(
                id=stable_chunk_id(doc.source, ordinal, text),
                text=text,
                source=doc.source,
                metadata=doc.metadata,
            )
        )
        ordinal += 1
        if start + size >= len(words):
            break
    return chunks


def chunk_all(docs: list[Document], cfg: Config) -> list[Chunk]:
    out: list[Chunk] = []
    seen: set[str] = set()
    for doc in docs:
        for c in chunk_document(doc, cfg):
            if c.id in seen:
                continue
            seen.add(c.id)
            out.append(c)
    return out
