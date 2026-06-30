"""Canonical records, config loading, and the vector packing that mirrors the Android side.

The chunk record maps 1:1 to the Android `KbChunkEntity` columns, and `pack_vector` writes the
exact little-endian float32 layout `KbConverters.unpack` reads. Keep both in sync.
"""
from __future__ import annotations

import hashlib
import struct
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml

REPO_TOOL_ROOT = Path(__file__).resolve().parents[2]  # tools/iris-kb


@dataclass
class Config:
    raw: dict[str, Any]

    @property
    def kb_version(self) -> int:
        return int(self.raw["kb_version"])

    @property
    def embedder_id(self) -> str:
        return str(self.raw["embedder"]["id"])

    @property
    def model_path(self) -> Path:
        return (REPO_TOOL_ROOT / self.raw["embedder"]["model_path"]).resolve()

    @property
    def normalize(self) -> bool:
        return bool(self.raw["embedder"].get("normalize", True))

    @property
    def max_tokens(self) -> int:
        return int(self.raw["chunk"]["max_tokens"])

    @property
    def overlap_tokens(self) -> int:
        return int(self.raw["chunk"]["overlap_tokens"])

    @property
    def assets_dir(self) -> Path:
        return (REPO_TOOL_ROOT / self.raw["output"]["app_assets_dir"]).resolve()

    @property
    def db_name(self) -> str:
        return f"iris_kb_v{self.kb_version}.db"

    @property
    def manifest_name(self) -> str:
        return f"iris_kb_v{self.kb_version}.manifest.json"

    @property
    def recall_at(self) -> list[int]:
        return list(self.raw["eval"]["recall_at"])

    @property
    def recall5_threshold(self) -> float:
        return float(self.raw["eval"]["recall5_threshold"])

    @property
    def parity_epsilon(self) -> float:
        return float(self.raw["eval"].get("parity_epsilon", 5e-4))


def load_config(path: Path | None = None) -> Config:
    path = path or (REPO_TOOL_ROOT / "config" / "kb_config.yaml")
    return Config(yaml.safe_load(path.read_text()))


@dataclass
class Document:
    """A normalized source document before chunking."""
    title: str
    body: str
    source: str          # e.g. "visa_entry/schengen"
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class Chunk:
    """A retrievable chunk. Mirrors KbChunkEntity (sans embedding, added at the embed stage)."""
    id: str
    text: str
    source: str
    source_type: str = "KB"
    sensitivity: str = "PUBLIC"
    metadata: dict[str, Any] = field(default_factory=dict)


def stable_chunk_id(source: str, ordinal: int, text: str) -> str:
    """Content-stable id so rebuilds are diffable and gold (qrels) stays valid."""
    h = hashlib.sha1(f"{source}|{ordinal}|{text}".encode("utf-8")).hexdigest()
    return h[:16]


def pack_vector(vector: list[float]) -> bytes:
    """Little-endian float32 — must match the Android KbConverters layout."""
    return struct.pack(f"<{len(vector)}f", *vector)


def unpack_vector(blob: bytes) -> list[float]:
    n = len(blob) // 4
    return list(struct.unpack(f"<{n}f", blob))
