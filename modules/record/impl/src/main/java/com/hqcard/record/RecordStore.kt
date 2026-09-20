package com.hqcard.record

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface SwipeEventDao {
    @Insert
    suspend fun insert(event: SwipeEvent): Long

    @Query("SELECT * FROM swipe_events ORDER BY swipedAt DESC, id DESC")
    fun observeAll(): Flow<List<SwipeEvent>>

    @Query(
        "SELECT * FROM swipe_events WHERE swipedAt >= :fromInclusive AND swipedAt < :toExclusive " +
            "ORDER BY swipedAt DESC, id DESC",
    )
    fun observeRange(fromInclusive: Long, toExclusive: Long): Flow<List<SwipeEvent>>

    @Query("DELETE FROM swipe_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM swipe_events")
    suspend fun clearAll()
}

@Database(entities = [SwipeEvent::class], version = 2, exportSchema = false)
abstract class HqCardDatabase : RoomDatabase() {
    abstract fun swipeEventDao(): SwipeEventDao
}

/** Room 实现的仓库：在 DAO 层之前完成契约要求的参数校验 */
internal class RoomSwipeEventRepository(
    private val dao: SwipeEventDao,
) : SwipeEventRepository {

    override suspend fun insert(swipedAt: Long, detail: String): SwipeEvent {
        require(swipedAt > 0) { "swipedAt must be positive" }
        require(detail.isNotBlank()) { "detail must not be blank" }
        val id = dao.insert(SwipeEvent(swipedAt = swipedAt, detail = detail))
        return SwipeEvent(id = id, swipedAt = swipedAt, detail = detail)
    }

    override fun observeAll(): Flow<List<SwipeEvent>> = dao.observeAll()

    override fun observeRange(fromInclusive: Long, toExclusive: Long): Flow<List<SwipeEvent>> =
        dao.observeRange(fromInclusive, toExclusive)

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun clear() = dao.clearAll()
}

/** 仓库工厂。契约：modules/record/docs/interface.md */
object SwipeRecordStore {

    @Volatile
    private var instance: SwipeEventRepository? = null

    /**
     * 进程级单例：detector 服务与 UI 共享同一 Room 实例，
     * 保证服务写入后 UI 的 Flow 立即收到更新。
     */
    fun get(context: Context): SwipeEventRepository =
        instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

    /** 每次新建实例（测试用） */
    fun create(context: Context): SwipeEventRepository {
        val db = Room.databaseBuilder(context, HqCardDatabase::class.java, "hqcard.db")
            // v1（SwipeRecord 读卡时代）→ v2（SwipeEvent 卡模拟时代）：本地调试数据无保留价值，直接重建
            .fallbackToDestructiveMigration()
            .build()
        return RoomSwipeEventRepository(db.swipeEventDao())
    }
}
