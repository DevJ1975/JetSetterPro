"""Retrieval-quality eval + embedder parity probe.

eval(): reads eval/qrels.jsonl, embeds each query, ranks all chunks in the built artifact by cosine,
and reports recall@k + MRR. A chunk counts as relevant if its id is in `relevant_chunk_ids` OR its
source starts with any entry in `relevant_sources` (the source form keeps gold stable across
content-hash id changes). Returns a nonzero exit upstream if recall@5 is below threshold.

verify_parity(): embeds a fixed probe sentence and compares it to eval/fixtures/probe_vector.json
(auto-captured on first run) within epsilon — catches model/preprocessing drift between this pipeline
and the on-device embedder.
"""
from __future__ import annotations

import json
import sqlite3
from pathlib import Path

import numpy as np

from .embed import Embedder
from .schema import Config, REPO_TOOL_ROOT, unpack_vector

EVAL_DIR = REPO_TOOL_ROOT / "eval"
QRELS = EVAL_DIR / "qrels.jsonl"
PROBE_FIXTURE = EVAL_DIR / "fixtures" / "probe_vector.json"
PROBE_SENTENCE = "What documents do I need to enter the Schengen area?"


def _load_artifact(cfg: Config):
    db_path = REPO_TOOL_ROOT / "dist" / cfg.db_name
    conn = sqlite3.connect(db_path)
    try:
        rows = conn.execute("SELECT id, source, embedding FROM kb_chunks").fetchall()
    finally:
        conn.close()
    ids = [r[0] for r in rows]
    sources = [r[1] for r in rows]
    mat = np.array([unpack_vector(r[2]) for r in rows], dtype=np.float32)
    return ids, sources, mat


def _is_relevant(cid: str, source: str, rel_ids: set[str], rel_sources: list[str]) -> bool:
    if cid in rel_ids:
        return True
    return any(source.startswith(p) for p in rel_sources)


def evaluate(cfg: Config) -> dict:
    if not QRELS.exists():
        raise FileNotFoundError(f"No gold file at {QRELS}")
    ids, sources, mat = _load_artifact(cfg)
    norms = np.linalg.norm(mat, axis=1, keepdims=True)
    norms[norms == 0] = 1.0
    mat_n = mat / norms

    embedder = Embedder(cfg)
    ks = sorted(cfg.recall_at)
    recall = {k: 0.0 for k in ks}
    rr_total = 0.0
    queries = [json.loads(line) for line in QRELS.read_text().splitlines() if line.strip()]

    for q in queries:
        qv = np.array(embedder.embed(q["query"]), dtype=np.float32)
        qn = qv / (np.linalg.norm(qv) or 1.0)
        scores = mat_n @ qn
        order = np.argsort(-scores)
        rel_ids = set(q.get("relevant_chunk_ids", []))
        rel_sources = q.get("relevant_sources", [])
        ranked_rel = [_is_relevant(ids[i], sources[i], rel_ids, rel_sources) for i in order]
        for k in ks:
            if any(ranked_rel[:k]):
                recall[k] += 1.0
        first = next((rank for rank, ok in enumerate(ranked_rel, start=1) if ok), None)
        rr_total += (1.0 / first) if first else 0.0

    n = max(1, len(queries))
    report = {
        "queries": len(queries),
        "recall": {f"@{k}": round(recall[k] / n, 4) for k in ks},
        "mrr": round(rr_total / n, 4),
    }
    return report


def write_report(cfg: Config, report: dict) -> Path:
    out_dir = EVAL_DIR / "report"
    out_dir.mkdir(parents=True, exist_ok=True)
    path = out_dir / f"v{cfg.kb_version}.md"
    lines = [
        f"# KB v{cfg.kb_version} retrieval eval",
        "",
        f"- queries: {report['queries']}",
        f"- MRR: {report['mrr']}",
        "",
        "| k | recall |",
        "|---|--------|",
    ]
    for k, v in report["recall"].items():
        lines.append(f"| {k} | {v} |")
    path.write_text("\n".join(lines) + "\n")
    return path


def verify_parity(cfg: Config) -> bool:
    embedder = Embedder(cfg)
    vec = embedder.embed(PROBE_SENTENCE)
    PROBE_FIXTURE.parent.mkdir(parents=True, exist_ok=True)
    if not PROBE_FIXTURE.exists():
        PROBE_FIXTURE.write_text(json.dumps({
            "sentence": PROBE_SENTENCE, "embedder_id": cfg.embedder_id, "vector": vec,
        }))
        print(f"Captured parity fixture at {PROBE_FIXTURE} ({len(vec)} dims).")
        return True
    golden = json.loads(PROBE_FIXTURE.read_text())
    gv = np.array(golden["vector"], dtype=np.float32)
    nv = np.array(vec, dtype=np.float32)
    if gv.shape != nv.shape:
        print(f"PARITY FAIL: dim {nv.shape} != fixture {gv.shape}")
        return False
    max_abs = float(np.max(np.abs(gv - nv)))
    ok = max_abs <= cfg.parity_epsilon
    print(f"parity max|Δ| = {max_abs:.6g} (epsilon {cfg.parity_epsilon}) -> {'OK' if ok else 'FAIL'}")
    return ok
