# Embedder model

Place the on-device text-embedder model here as `use_embedder.tflite`.

This is the **parity anchor**: the *same* file must be bundled in the app at
`app/src/main/assets/use_embedder.tflite`. If the two ever differ, retrieval silently degrades.

## Recommended model (v1): Universal Sentence Encoder (MediaPipe Text Embedder)

MediaPipe's Text Embedder ships a compatible Universal Sentence Encoder `.tflite` (~web of a few MB,
100-dim output). Get the current asset from the MediaPipe Text Embedder docs/model card:

- https://ai.google.dev/edge/mediapipe/solutions/text/text_embedder

Steps:
1. Download the Universal Sentence Encoder `.tflite` from the MediaPipe Text Embedder model page.
2. Save it as `tools/iris-kb/models/use_embedder.tflite`.
3. Copy the same file to `app/src/main/assets/use_embedder.tflite`.
4. Record its checksum below so drift is detectable:

```
sha256:  <fill in after download>
source:  <model card URL + version>
```

> Weights are git-ignored (see `.gitignore`). Commit only this record, not the binary.

## Upgrade path (v2): EmbeddingGemma (308M)

Higher quality + multilingual, MRL-truncatable. Requires the LiteRT-LM runtime on-device and pinning
one MRL dimension + the model's task prompt prefixes on **both** sides. Defer until v1 retrieval
quality is measured and found wanting.
