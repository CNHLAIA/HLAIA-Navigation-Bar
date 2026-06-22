package com.hlaia.event;

/**
 * 【ES 搜索同步事件】—— Service 发布、SearchSyncEventListener 消费的事务事件载体
 *
 * 工作机制：
 *   Service 在 @Transactional 方法内 publishEvent(new SearchSyncEvent(...))
 *   → Spring 暂存事件，**不立即处理**
 *   → 事务成功提交后，触发 SearchSyncEventListener（@TransactionalEventListener AFTER_COMMIT）
 *   → 监听器调 SearchSyncService 写 ES
 *   → 若事务回滚，事件被丢弃，ES 不写入，天然一致
 *
 * 为什么用事件而不是直接调 SearchSyncService？
 *   若在事务内直接写 ES，MySQL 回滚时 ES 会残留脏数据。
 *   AFTER_COMMIT 阶段才写 ES，彻底消除这一不一致窗口。
 *
 * 字段说明：
 *   - type：数据类型（"bookmark" / "folder"），监听器据此路由
 *   - action：操作类型（CREATE/UPDATE/DELETE），目前 upsert 已统一处理 CREATE+UPDATE，
 *             保留此字段是为了未来扩展（如增量更新策略）
 *   - id：被操作数据的 ID，监听器据此让 SearchSyncService 从 MySQL 查最新值
 */
public record SearchSyncEvent(String type, String action, Long id) {
}
