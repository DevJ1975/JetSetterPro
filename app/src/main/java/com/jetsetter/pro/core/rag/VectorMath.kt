package com.jetsetter.pro.core.rag

import kotlin.math.sqrt

/** Vector helpers for on-device retrieval. */
object VectorMath {

    /**
     * Cosine similarity in [-1, 1]. Works whether or not the inputs are L2-normalized (the embedder
     * is configured to normalize, in which case this reduces to a dot product). Returns 0 for a
     * length mismatch or a zero vector rather than throwing — retrieval should degrade, not crash.
     */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            na += x * x
            nb += y * y
        }
        if (na == 0.0 || nb == 0.0) return 0f
        return (dot / (sqrt(na) * sqrt(nb))).toFloat()
    }
}
