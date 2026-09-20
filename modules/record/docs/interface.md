---
module: record
---

# record Interface

## Types

### SwipeEvent
| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| id | Long | 是 | 主键，数据库自增；未持久化时为 0 |
| swipedAt | Long | 是 | 刷卡时刻 epoch 毫秒，`> 0` |
| detail | String | 是 | 非空白字符串（事件描述，如触发来源） |

## API

### interface SwipeEventRepository

#### insert(swipedAt: Long, detail: String) → SwipeEvent (suspend)
校验入参后插入一条事件，返回携带真实自增 id 的 `SwipeEvent`。

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| swipedAt | Long | 是 | 刷卡时间 epoch 毫秒，`> 0` |
| detail | String | 是 | 事件描述，非空白 |

**Errors:**
- `IllegalArgumentException("swipedAt must be positive")` — swipedAt <= 0
- `IllegalArgumentException("detail must not be blank")` — detail 为空白

#### observeAll() → Flow<List<SwipeEvent>>
返回全部事件的实时流，**按 `swipedAt` 倒序（新→旧），同一毫秒按 `id` 倒序**。数据库变更时自动重新发射。

#### observeRange(fromInclusive: Long, toExclusive: Long) → Flow<List<SwipeEvent>>
返回 **`swipedAt ∈ [fromInclusive, toExclusive)`**（下界含、上界不含）事件的实时流，
排序与 `observeAll` 一致（`swipedAt` 倒序，同毫秒 `id` 倒序）。数据库变更时自动重新发射。
供日历界面按天/按月筛选使用。

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| fromInclusive | Long | 是 | 区间下界（含），epoch 毫秒 |
| toExclusive | Long | 是 | 区间上界（不含），epoch 毫秒 |

#### delete(id: Long) (suspend)
删除指定 id 的事件；id 不存在时为空操作。

#### clear() (suspend)
删除全部事件。

### object SwipeRecordStore
- `get(context: Context): SwipeEventRepository` — 进程级单例（detector 服务与 UI 共享，保证服务写入后 UI 立即刷新）
- `create(context: Context): SwipeEventRepository` — 每次新建（测试用），数据库文件 `hqcard.db`，`fallbackToDestructiveMigration`

**Usage Example:**
```kotlin
val repo = SwipeRecordStore.get(context)
val saved = repo.insert(System.currentTimeMillis(), "小米钱包刷卡（卡片界面唤出）")
repo.observeAll().collect { list -> /* 渲染列表 */ }
// 日历按天筛选：
val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
repo.observeRange(dayStart, dayEnd).collect { dayEvents -> /* 渲染当天记录 */ }
```

## Storage
Room / SQLite。表 `swipe_events`：

| Column | Type | Constraints |
|--------|------|-------------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT |
| swipedAt | INTEGER | NOT NULL |
| detail | TEXT | NOT NULL |

数据库文件名：`hqcard.db`（version 2）。
