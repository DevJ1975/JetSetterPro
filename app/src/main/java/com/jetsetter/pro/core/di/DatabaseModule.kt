package com.jetsetter.pro.core.di

import android.content.Context
import androidx.room.Room
import com.jetsetter.pro.core.data.local.ExpenseDao
import com.jetsetter.pro.core.data.local.JetSetterDatabase
import com.jetsetter.pro.core.data.local.KbChunkDao
import com.jetsetter.pro.core.data.local.MIGRATION_1_2
import com.jetsetter.pro.core.data.local.MIGRATION_2_3
import com.jetsetter.pro.core.data.local.TripDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JetSetterDatabase =
        Room.databaseBuilder(context, JetSetterDatabase::class.java, "jetsetter.db")
            // Explicit migrations for every schema bump (see core/data/local/Migrations.kt).
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            // Destroy-and-recreate only on DOWNGRADE (e.g. a dev installing an older build). On an
            // UPGRADE we deliberately do NOT fall back to a destructive migration: a missing
            // migration now fails loudly instead of silently wiping the user's trips/expenses.
            // Add a Room Migration for each future schema bump (and enable exportSchema to
            // author/verify them — see app/build.gradle.kts).
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun provideTripDao(db: JetSetterDatabase): TripDao = db.tripDao()

    @Provides
    fun provideExpenseDao(db: JetSetterDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideKbChunkDao(db: JetSetterDatabase): KbChunkDao = db.kbChunkDao()
}
