---
module: record
---

# record Verification

> 测试方式：Robolectric + `Room.inMemoryDatabaseBuilder` 内存库 + `runTest`。

## Unit Tests

### insert
- [x] 合法入参插入成功，返回值 `id > 0` 且 `swipedAt` / `detail` 与入参一致
- [x] 连续插入两条，id 递增且不相同
- [x] `detail = ""` → 抛出 `IllegalArgumentException("detail must not be blank")`
- [x] `detail = "  "`（纯空白）→ 抛出 `IllegalArgumentException("detail must not be blank")`
- [x] `swipedAt = 0` → 抛出 `IllegalArgumentException("swipedAt must be positive")`
- [x] `swipedAt = -1` → 抛出 `IllegalArgumentException("swipedAt must be positive")`

### observeAll
- [x] 空库时发射空列表
- [x] 插入 3 条不同时间的事件，列表按 swipedAt 倒序
- [x] 插入相同 swipedAt 的 2 条事件，id 大的排前面
- [x] 插入新事件后 Flow 自动发射更新后的列表

### delete
- [x] 删除存在的 id 后该事件消失，其余事件不受影响
- [x] 删除不存在的 id 不崩溃、不影响其他事件

### clear
- [x] 清空后 `observeAll` 发射空列表

## Integration Tests
- [ ] 真机手动验收：杀进程重启 App 后历史记录仍在（Room 落盘）
- [ ] 真机手动验收：HCE 服务在界面未打开时写入的事件，打开 App 后可见

## Invariants（不变量）
- `detail` 永不为空白，`swipedAt` 永远 `> 0`（插入前强制校验）
- `id` 全局唯一且自增
- `observeAll` 返回的列表永远按 swipedAt 倒序（同毫秒按 id 倒序）
- 记录一旦插入不可被修改（接口不提供 update）
- `get()` 单例与 `create()` 新实例指向同一数据库文件时，写入对彼此可见（Room 表失效通知）
