package org.amisles.v4aw.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.amisles.v4aw.download.DownloadChunkDao
import org.amisles.v4aw.download.DownloadDao
import org.amisles.v4aw.model.DownloadChunkInfo
import org.amisles.v4aw.model.DownloadInfo
import org.amisles.v4aw.model.HistoryItem
import org.amisles.v4aw.data.local.dao.HistoryDao

@Database(entities = [HistoryItem::class, DownloadInfo::class, DownloadChunkInfo::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun downloadChunkDao(): DownloadChunkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `download_chunks` (`downloadId` TEXT NOT NULL, `chunkIndex` INTEGER NOT NULL, `startByte` INTEGER NOT NULL, `endByte` INTEGER NOT NULL, `downloadedBytes` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `filePath` TEXT NOT NULL, PRIMARY KEY(`downloadId`, `chunkIndex`))")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `threadCount` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `supportsRange` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "v4aw_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
