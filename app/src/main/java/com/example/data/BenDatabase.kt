package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BenStateEntity::class], version = 1, exportSchema = false)
abstract class BenDatabase : RoomDatabase() {
    abstract fun benDao(): BenDao

    companion object {
        @Volatile
        private var INSTANCE: BenDatabase? = null

        fun getDatabase(context: Context): BenDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BenDatabase::class.java,
                    "ben_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun setTestDatabase(database: BenDatabase) {
            INSTANCE = database
        }
    }
}
