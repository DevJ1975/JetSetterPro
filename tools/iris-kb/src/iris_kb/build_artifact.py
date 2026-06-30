"""Builds the on-device artifact: a SQLite DB whose `kb_chunks` table matches the Android
`KbChunkEntity` exactly, plus a manifest describing the embedder + dim that built it.

The DDL here MUST stay identical to MIGRATION_2_3 in the app so the pre-built DB drops straight into
Room.
"""
from __future__ import annotations

import json
import shutil
import sqlite3
import time
from pathlib import Path

from .schema import Chunk, Config, pack_vector, REPO_TOOL_ROOT

DIST_DIR = REPO_TOOL_ROOT / "dist"

CREATE_TABLE = """
CREATE TABLE IF NOT EXISTS kb_chunks (
    id TEXT NOT NULL PRIMARY KEY,
    text TEXT NOT NULL,
    source TEXT NOT NULL,
    sourceType TEXT NOT NULL,
    sensitivity TEXT NOT NULL,
    embedding BLOB NOT NULL,
    dim INTEGER NOT NULL,
    modelId TEXT NOT NULL,
    metadata TEXT NOT NULL,
    updatedAt INTEGER NOT NULL
)
"""


def build(cfg: Config, chunks: list[Chunk], vectors: list[list[float]], dim: int,
          now_ms: int | None = None) -> tuple[Path, Path]:
    DIST_DIR.mkdir(parents=True, exist_ok=True)
    db_path = DIST_DIR / cfg.db_name
    manifest_path = DIST_DIR / cfg.manifest_name
    if db_path.exists():
        db_path.unlink()
    now_ms = now_ms if now_ms is not None else int(time.time() * 1000)

    conn = sqlite3.connect(db_path)
    try:
        conn.execute(CREATE_TABLE)
        conn.executemany(
            "INSERT OR REPLACE INTO kb_chunks "
            "(id, text, source, sourceType, sensitivity, embedding, dim, modelId, metadata, updatedAt) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            [
                (
                    c.id, c.text, c.source, c.source_type, c.sensitivity,
                    pack_vector(v), dim, cfg.embedder_id, json.dumps(c.metadata, sort_keys=True), now_ms,
                )
                for c, v in zip(chunks, vectors)
            ],
        )
        conn.commit()
    finally:
        conn.close()

    manifest = {
        "kb_version": cfg.kb_version,
        "embedder_id": cfg.embedder_id,
        "embedding_dim": dim,
        "normalize": cfg.normalize,
        "chunk_count": len(chunks),
        "built_at": now_ms,
    }
    manifest_path.write_text(json.dumps(manifest, indent=2))
    return db_path, manifest_path


def copy_to_assets(cfg: Config, db_path: Path, manifest_path: Path) -> None:
    assets = cfg.assets_dir
    assets.mkdir(parents=True, exist_ok=True)
    shutil.copy2(db_path, assets / cfg.db_name)
    shutil.copy2(manifest_path, assets / cfg.manifest_name)
