package com.jetsetter.pro.core.kb

/**
 * Version coordinates for the bundled general-travel KB.
 *
 * [CURRENT] is kept in lockstep with `tools/iris-kb` `manifest.json` `kb_version`. Bumping it
 * triggers a re-seed (the old PUBLIC chunks are replaced) and uses a fresh seed flag so an upgraded
 * install picks up the new KB exactly once. Asset names are versioned so two KB builds never collide.
 */
object KbVersion {
    const val CURRENT = 1

    /** Pre-built SQLite KB whose `kb_chunks` table matches `KbChunkEntity` (bundled in assets). */
    val assetDb: String get() = "iris_kb_v$CURRENT.db"

    /** Sidecar manifest describing the embedder + dim that built [assetDb]. */
    val assetManifest: String get() = "iris_kb_v$CURRENT.manifest.json"

    /** DataStore flag (mirrors `trips_seeded_v2`) so the KB seeds once per version. */
    val seedFlagKey: String get() = "kb_seeded_v$CURRENT"
}
