package com.taisau.android.common.loggger.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 日志记录的 Room 数据访问对象。
 */
@Dao
internal interface LogDao {

    /** 插入一条日志记录。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LogEntity)

    /** 批量插入日志记录。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<LogEntity>)

    /** 按 ID 查询单条记录。 */
    @Query("SELECT * FROM ${LogEntity.TABLE_NAME} WHERE id = :id")
    suspend fun getById(id: Long): LogEntity?

    /** 查询全部日志（按时间倒序）。 */
    @Query("SELECT * FROM ${LogEntity.TABLE_NAME} ORDER BY timestamp DESC")
    fun getAll(): Flow<List<LogEntity>>

    /** 按级别过滤查询。 */
    @Query("SELECT * FROM ${LogEntity.TABLE_NAME} WHERE level >= :minLevel ORDER BY timestamp DESC")
    fun getByMinLevel(minLevel: Int): Flow<List<LogEntity>>

    /** 按标签模糊查询。 */
    @Query("SELECT * FROM ${LogEntity.TABLE_NAME} WHERE tag LIKE '%' || :tag || '%' ORDER BY timestamp DESC")
    fun getByTag(tag: String): Flow<List<LogEntity>>

    /** 分页查询（供 ContentProvider 使用，非 Flow）。 */
    @Query("SELECT * FROM ${LogEntity.TABLE_NAME} ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<LogEntity>

    /** 按时间范围查询。 */
    @Query("SELECT * FROM ${LogEntity.TABLE_NAME} WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getByTimeRange(startTime: Long, endTime: Long): List<LogEntity>

    /** 删除指定 ID 的记录。 */
    @Query("DELETE FROM ${LogEntity.TABLE_NAME} WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 清空所有日志。 */
    @Query("DELETE FROM ${LogEntity.TABLE_NAME}")
    suspend fun deleteAll()

    /** 清理指定时间之前的日志。 */
    @Query("DELETE FROM ${LogEntity.TABLE_NAME} WHERE timestamp < :beforeTime")
    suspend fun deleteOlderThan(beforeTime: Long)

    /** 获取日志总数。 */
    @Query("SELECT COUNT(*) FROM ${LogEntity.TABLE_NAME}")
    suspend fun count(): Int
}
