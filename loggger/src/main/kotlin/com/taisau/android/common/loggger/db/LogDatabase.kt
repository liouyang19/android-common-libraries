package com.taisau.android.common.loggger.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 日志记录的 Room 数据库。
 *
 * 默认数据库名 [DEFAULT_DB_NAME]，可通过 [Builder] 自定义。
 */
@Database(
    entities = [LogEntity::class],
    version = 1,
    exportSchema = false
)
internal abstract class LogDatabase : RoomDatabase() {

    abstract fun logDao(): LogDao

    companion object {
        private const val DEFAULT_DB_NAME = "loggger_database.db"

        /** 创建数据库实例（单例，内部同步锁）。 */
        @Volatile
        private var instance: LogDatabase? = null

        fun getInstance(context: Context, dbName: String = DEFAULT_DB_NAME): LogDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LogDatabase::class.java,
                    dbName
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { instance = it }
            }
        }

        /** 仅用于测试：重置单例。 */
        internal fun resetForTest() {
            instance = null
        }
    }
}
