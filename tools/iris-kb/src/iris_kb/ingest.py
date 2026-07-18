"""Ingest curated sources (markdown / JSON) into normalized Documents.

Source path convention: `sources/<category>/<name>.{md,json}` → source id `"<category>/<name>"`.
Markdown: first `# Heading` is the title, the rest is the body. JSON: a list of
`{title, body, metadata?}` objects, or a single such object.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

from .schema import Document, REPO_TOOL_ROOT

SOURCES_DIR = REPO_TOOL_ROOT / "sources"


def _source_id(path: Path) -> str:
    rel = path.relative_to(SOURCES_DIR).with_suffix("")
    return str(rel).replace("\\", "/")


def _normalize(text: str) -> str:
    # Collapse runs of whitespace but keep paragraph breaks.
    text = text.replace("\r\n", "\n")
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def _ingest_markdown(path: Path) -> Document:
    raw = path.read_text(encoding="utf-8")
    title = ""
    m = re.search(r"^#\s+(.+)$", raw, flags=re.MULTILINE)
    if m:
        title = m.group(1).strip()
        raw = raw[: m.start()] + raw[m.end():]
    return Document(title=title or path.stem, body=_normalize(raw), source=_source_id(path),
                    metadata={"category": _source_id(path).split("/")[0]})


def _ingest_json(path: Path) -> list[Document]:
    data = json.loads(path.read_text(encoding="utf-8"))
    entries = data if isinstance(data, list) else [data]
    docs: list[Document] = []
    for i, e in enumerate(entries):
        body = _normalize(str(e.get("body", "")))
        if not body:
            continue
        src = _source_id(path) + (f"#{i}" if len(entries) > 1 else "")
        meta = dict(e.get("metadata", {}))
        meta.setdefault("category", _source_id(path).split("/")[0])
        docs.append(Document(title=str(e.get("title", path.stem)), body=body, source=src, metadata=meta))
    return docs


def ingest() -> list[Document]:
    if not SOURCES_DIR.exists():
        return []
    docs: list[Document] = []
    for path in sorted(SOURCES_DIR.rglob("*")):
        if path.suffix.lower() == ".md":
            docs.append(_ingest_markdown(path))
        elif path.suffix.lower() == ".json":
            docs.extend(_ingest_json(path))
    return docs
