"""Pipeline entry point. Usage: python -m iris_kb.cli <ingest|chunk|build|eval|verify-parity|all>"""
from __future__ import annotations

import sys

from . import build_artifact, eval as kb_eval
from .chunk import chunk_all
from .embed import Embedder
from .ingest import ingest
from .schema import load_config


def cmd_ingest() -> int:
    docs = ingest()
    print(f"ingested {len(docs)} document(s)")
    return 0


def cmd_chunk() -> int:
    cfg = load_config()
    chunks = chunk_all(ingest(), cfg)
    print(f"chunked into {len(chunks)} chunk(s)")
    return 0


def cmd_build() -> int:
    cfg = load_config()
    chunks = chunk_all(ingest(), cfg)
    if not chunks:
        print("no chunks to build (add sources/ content)", file=sys.stderr)
        return 1
    embedder = Embedder(cfg)
    vectors = embedder.embed_all([c.text for c in chunks])
    dim = embedder.dimension or (len(vectors[0]) if vectors else 0)
    db_path, manifest_path = build_artifact.build(cfg, chunks, vectors, dim)
    build_artifact.copy_to_assets(cfg, db_path, manifest_path)
    print(f"built {db_path.name} ({len(chunks)} chunks, dim {dim}) and copied to app assets")
    return 0


def cmd_eval() -> int:
    cfg = load_config()
    report = kb_eval.evaluate(cfg)
    path = kb_eval.write_report(cfg, report)
    print(report)
    print(f"report: {path}")
    recall5 = report["recall"].get("@5", 0.0)
    if recall5 < cfg.recall5_threshold:
        print(f"FAIL: recall@5 {recall5} < threshold {cfg.recall5_threshold}", file=sys.stderr)
        return 1
    return 0


def cmd_verify_parity() -> int:
    cfg = load_config()
    return 0 if kb_eval.verify_parity(cfg) else 1


def main(argv: list[str]) -> int:
    cmds = {
        "ingest": cmd_ingest,
        "chunk": cmd_chunk,
        "build": cmd_build,
        "eval": cmd_eval,
        "verify-parity": cmd_verify_parity,
    }
    if len(argv) != 1 or (argv[0] != "all" and argv[0] not in cmds):
        print(f"usage: python -m iris_kb.cli <{'|'.join(cmds)}|all>", file=sys.stderr)
        return 2
    if argv[0] == "all":
        for c in ("build", "eval", "verify-parity"):
            rc = cmds[c]()
            if rc != 0:
                return rc
        return 0
    return cmds[argv[0]]()


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
