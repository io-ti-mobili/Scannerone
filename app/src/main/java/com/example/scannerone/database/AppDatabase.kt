package com.example.scannerone.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.entities.WifiScanRecord
import com.example.scannerone.entities.ScanSession

@Database(entities = [WifiNetwork::class, WifiScanRecord::class, ScanSession::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun networkDao(): NetworkDao
    abstract fun searchDao(): SearchDao
    abstract fun mapDao(): MapDao
    abstract fun sessionDao(): SessionDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun importExportDao(): ImportExportDao

    companion object {
        @Volatile
        private var Instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scannerone_database"
                )
                    .fallbackToDestructiveMigration(true)
                .build()
                .also { Instance = it }
            }
        }
    }
}
