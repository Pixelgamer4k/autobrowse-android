package com.autobrowse.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.autobrowse.android.data.local.dao.AutomationTaskDao
import com.autobrowse.android.data.local.dao.BrowserTabDao
import com.autobrowse.android.data.local.dao.ChatMessageDao
import com.autobrowse.android.data.local.dao.MemoryDao
import com.autobrowse.android.data.local.dao.SessionDao
import com.autobrowse.android.data.local.dao.StrategyDao
import com.autobrowse.android.data.local.dao.TrajectoryDao
import com.autobrowse.android.data.local.entity.AutomationTaskEntity
import com.autobrowse.android.data.local.entity.BrowserTabEntity
import com.autobrowse.android.data.local.entity.ChatMessageEntity
import com.autobrowse.android.data.local.entity.MemoryEntryEntity
import com.autobrowse.android.data.local.entity.MemoryFtsEntity
import com.autobrowse.android.data.local.entity.SessionEntity
import com.autobrowse.android.data.local.entity.StrategyEntity
import com.autobrowse.android.data.local.entity.TrajectoryEntity

@Database(
    entities = [
        SessionEntity::class,
        ChatMessageEntity::class,
        MemoryEntryEntity::class,
        MemoryFtsEntity::class,
        AutomationTaskEntity::class,
        BrowserTabEntity::class,
        StrategyEntity::class,
        TrajectoryEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AutobrowseDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun automationTaskDao(): AutomationTaskDao
    abstract fun browserTabDao(): BrowserTabDao
    abstract fun strategyDao(): StrategyDao
    abstract fun trajectoryDao(): TrajectoryDao

    companion object {
        @Volatile
        private var instance: AutobrowseDatabase? = null

        fun get(context: Context): AutobrowseDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AutobrowseDatabase::class.java,
                    "autobrowse.db",
                )
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
        }
    }
}