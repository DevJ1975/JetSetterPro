package com.jetsetter.pro.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations for [JetSetterDatabase]. Every version bump needs an explicit migration here:
 * [com.jetsetter.pro.core.di.DatabaseModule] only rebuilds destructively on a *downgrade*, so a
 * forgotten upgrade migration fails loudly instead of silently wiping the user's trips/expenses.
 */

/**
 * v1 → v2: adds the `expenses` table (Phase A moved expenses from the ModuleStateStore JSON blob
 * into Room). `trips` is unchanged since v1, so this is purely additive — a v1 install upgrades
 * with all trips preserved. DDL matches Room's generated schema for [ExpenseEntity] exactly.
 */
val MIGRATION_1_2 = Migration(1, 2) { db: SupportSQLiteDatabase ->
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `expenses` (
            `id` TEXT NOT NULL,
            `amount` REAL NOT NULL,
            `currency` TEXT NOT NULL,
            `category` TEXT NOT NULL,
            `merchant` TEXT NOT NULL,
            `date` TEXT NOT NULL,
            `notes` TEXT,
            PRIMARY KEY(`id`)
        )
        """.trimIndent(),
    )
}

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
