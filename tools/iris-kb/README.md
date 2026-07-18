# iris-kb — IRIS on-device knowledge base builder

This is the **offline knowledge/embedding pipeline** that builds IRIS's on-device RAG knowledge
base. It ingests curated travel knowledge, chunks it, embeds it **with the exact same model the
Android app runs**, evaluates retrieval quality, and emits a pre-built SQLite artifact the app seeds
on first launch.

> **This is RAG, not fine-tuning.** Nothing here trains or updates Gemini Nano's weights (apps
> can't). "Training IRIS" = a better *retrieval corpus* (here) plus the on-device *preference loop*
> (`core/intelligence/UserMemory` in the app). No gradients, no model weights are produced.

## The one hard constraint: embedder parity

The vectors built here must live in the **same vector space** as the app's on-device embedder
(`com.jetsetter.pro.core.rag.MediaPipeTextEmbedder`). That means **identical**: the `.tflite` model
file, the model id, the output dimension, L2-normalization, and any preprocessing. Both sides run
the *same* `use_embedder.tflite` through MediaPipe Tasks `TextEmbedder` — the strongest guarantee of
parity. Do **not** swap in a different embedding library offline; a different graph = a different
space = silent retrieval failure.

- Model id here (`config/kb_config.yaml` → `embedder.id`) **must equal** `MediaPipeTextEmbedder.MODEL_ID` (`use-v1`).
- The model file `models/use_embedder.tflite` is the same file bundled at
  `app/src/main/assets/use_embedder.tflite`.

## Layout

```
config/kb_config.yaml      # embedder id/dim/normalize, chunk sizes, output paths, kb_version, eval thresholds
models/                    # use_embedder.tflite (git-ignored; see models/DOWNLOAD.md)
sources/                   # curated raw knowledge (markdown/json), version-controlled
  visa_entry/ baggage/ loyalty/ packing/ etiquette/ airlines/ airports/
src/iris_kb/               # the pipeline (ingest → chunk → embed → build → eval → verify-parity)
eval/qrels.jsonl           # gold question → relevant source(s)/chunk id(s)
eval/fixtures/             # probe_vector.json (parity anchor; auto-captured on first verify-parity)
eval/report/               # generated eval reports
dist/                      # built artifact + manifest (also copied into app assets by `make build`)
```

## Usage

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
# place the embedder model at models/use_embedder.tflite (see models/DOWNLOAD.md)

make build          # ingest + chunk + embed + write SQLite artifact + manifest, copy into app assets
make eval           # recall@{1,3,5,10} + MRR over eval/qrels.jsonl; fails if recall@5 < threshold
make verify-parity  # embed a fixed probe; compare to eval/fixtures/probe_vector.json within epsilon
```

`make build` writes `dist/iris_kb_v<n>.db` + `dist/iris_kb_v<n>.manifest.json` and copies both into
`app/src/main/assets/` (alongside `use_embedder.tflite`). Bump `kb_version` in `kb_config.yaml` and
`KbVersion.CURRENT` in the app together when the corpus changes — the app re-seeds once per version.

## Adding knowledge

Drop markdown or JSON into the right `sources/<category>/` folder, add gold rows to
`eval/qrels.jsonl`, then `make build eval`. Chunk ids are content-stable hashes, so rebuilds are
diffable and existing gold stays valid.
