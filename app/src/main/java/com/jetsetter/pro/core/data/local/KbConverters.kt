package com.jetsetter.pro.core.data.local

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Packs/unpacks embedding vectors for the [KbChunkEntity.embedding] BLOB column as little-endian
 * float32.
 *
 * Kept separate from the Moshi-based [Converters] (this is raw binary, not JSON) and used explicitly
 * by KbRepository/seeding rather than registered as a Room `@TypeConverter`, so the column stays a
 * plain BLOB. The offline KB builder (`tools/iris-kb`) writes bytes in this exact layout, which is
 * what lets a pre-built SQLite KB drop straight into Room.
 */
object KbConverters {

    fun pack(vector: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buf.putFloat(it) }
        return buf.array()
    }

    fun unpack(bytes: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(bytes.size / Float.SIZE_BYTES)
        for (i in out.indices) out[i] = buf.float
        return out
    }
}
