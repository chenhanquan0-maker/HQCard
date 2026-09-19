package com.hqcard.record

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.flow.Flow

/** 一次"被刷卡"事件：手机作为卡被读卡器刷过的记录。契约：modules/record/docs/interface.md */
@Entity(tableName = "swipe_events")
data class SwipeEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 刷卡时刻（epoch 毫秒） */
    val swipedAt: Long,
    /** 事件描述，如触发来源 */
    val detail: String,
)

/** 刷卡事件仓库。契约：modules/record/docs/interface.md */
interface SwipeEventRepository {
    /** 校验并插入事件，返回携带自增 id 的事件 */
    suspend fun insert(swipedAt: Long, detail: String): SwipeEvent

    /** 全部事件的实时流：swipedAt 倒序，同毫秒 id 倒序 */
    fun observeAll(): Flow<List<SwipeEvent>>

    /** 删除指定 id；不存在时为空操作 */
    suspend fun delete(id: Long)

    /** 清空全部事件 */
    suspend fun clear()
}
