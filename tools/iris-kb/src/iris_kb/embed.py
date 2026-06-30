"""The SHARED embedder wrapper — MediaPipe Tasks TextEmbedder over the same .tflite the app runs.

This is the parity anchor. Any change to the model file, normalization, or library here must be
mirrored in com.jetsetter.pro.core.rag.MediaPipeTextEmbedder, or the two vector spaces diverge.
"""
from __future__ import annotations

from pathlib import Path

from .schema import Config


class Embedder:
    def __init__(self, cfg: Config):
        model_path: Path = cfg.model_path
        if not model_path.exists():
            raise FileNotFoundError(
                f"Embedder model not found at {model_path}. See tools/iris-kb/models/DOWNLOAD.md."
            )
        # Imported lazily so ingest/chunk work without mediapipe installed.
        from mediapipe.tasks import python as mp_python
        from mediapipe.tasks.python import text as mp_text

        base = mp_python.BaseOptions(model_asset_path=str(model_path))
        options = mp_text.TextEmbedderOptions(
            base_options=base,
            l2_normalize=cfg.normalize,
            quantize=False,
        )
        self._embedder = mp_text.TextEmbedder.create_from_options(options)
        self.dimension: int | None = None

    def embed(self, text: str) -> list[float]:
        result = self._embedder.embed(text)
        vector = list(result.embeddings[0].embedding)
        self.dimension = len(vector)
        return vector

    def embed_all(self, texts: list[str]) -> list[list[float]]:
        return [self.embed(t) for t in texts]
