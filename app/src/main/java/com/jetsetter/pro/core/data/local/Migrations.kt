package com.jetsetter.pro.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations for [JetSetterDatabase]. Every version bump needs an explicit migration here:
 * [com.jetsetter.pro.core.di.DatabaseModule] only rebuilds destructively on a *downgrade*, so a
 * forgotten upgrade migration fails loudly instead of silently wiping the user's trips/expenses.
 */

/**
 * v2 → v3: adds the `kb_chunks` table backing IRIS's on-device RAG knowledge base. Purely additive
 * — it cannot touch `trips`/`expenses`, so existing user data is preserved across the upgrade.
 */
val MIGRATION_2_3 = Migration(2, 3) { db: SupportSQLiteDatabase ->
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `kb_chunks` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `text` TEXT NOT NULL,
            `source` TEXT NOT NULL,
            `sourceType` TEXT NOT NULL,
            `sensitivity` TEXT NOT NULL,
            `embedding` BLOB NOT NULL,
            `dim` INTEGER NOT NULL,
            `modelId` TEXT NOT NULL,
            `metadata` TEXT NOT NULL,
            `updatedAt` INTEGER NOT NULL
        )
        """.trimIndent(),
    )
}
