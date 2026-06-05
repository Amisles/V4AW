package org.amisles.v4aw.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.amisles.v4aw.download.DownloadChunkDao
import org.amisles.v4aw.download.DownloadDao
import org.amisles.v4aw.model.DownloadChunkInfo
import org.amisles.v4aw.model.DownloadInfo
import org.amisles.v4aw.model.HistoryItem
import org.amisles.v4aw.model.SiteRule
import org.amisles.v4aw.data.local.dao.HistoryDao
import org.amisles.v4aw.data.local.dao.SiteRuleDao

@Database(entities = [HistoryItem::class, DownloadInfo::class, DownloadChunkInfo::class, SiteRule::class], version = 5, exportSchema = false)
@TypeConverters(SiteRuleConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun downloadChunkDao(): DownloadChunkDao
    abstract fun siteRuleDao(): SiteRuleDao

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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `site_rules` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `urlPattern` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `priority` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `videoSourceRule` TEXT, `videoEntryRule` TEXT, `searchEndpointRule` TEXT, `webViewConfig` TEXT, PRIMARY KEY(`id`))")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "v4aw_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
