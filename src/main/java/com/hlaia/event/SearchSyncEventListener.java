package com.hlaia.event;

import com.hlaia.service.SearchSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 【ES 搜索同步事件监听器】—— 事务提交后同步 MySQL 数据到 ES
 *
 * 核心机制：{@link TransactionalEventListener} 配合 {@link TransactionPhase#AFTER_COMMIT}
 *   保证只有当外层 @Transactional 事务**成功提交**后，才执行 ES 同步。
 *   事务回滚时事件被丢弃，不会写出脏数据到 ES。
 *
 * 为什么用 AFTER_COMMIT 而不是默认的 BEFORE_COMMIT？
 *   BEFORE_COMMIT 阶段事务还未提交，此时写 ES 若事务后续回滚就会产生不一致。
 *   AFTER_COMMIT 才是"业务已成功落库"的安全时机。
 *
 * 为什么监听器内仍需 try/catch？
 *   事务已提交，此时 ES 写入失败不能再回滚 MySQL（否则数据反而不一致）。
 *   只能 log.warn 记录，靠 ElasticsearchDataInitializer（启动兜底）或
 *   /api/search/reindex（手动重建）最终修复。
 *
 * 注：fallbackExecution 默认为 false——如果调用方不在事务上下文内，事件会被丢弃。
 *   本项目所有调用点都在 @Transactional 方法内（见 BookmarkService/FolderService），
 *   因此无需开启 fallbackExecution。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchSyncEventListener {

    private final SearchSyncService searchSyncService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSearchSync(SearchSyncEvent event) {
        try {
            switch (event.type()) {
                case "bookmark" -> handleBookmark(event.action(), event.id());
                case "folder" -> handleFolder(event.action(), event.id());
                default -> log.warn("Unknown SearchSyncEvent type: {}", event.type());
            }
        } catch (Exception e) {
            // 事务已提交，ES 失败只能记日志，靠 reindex 兜底
            log.warn("ES sync failed after commit (type={}, action={}, id={}): {}",
                    event.type(), event.action(), event.id(), e.getMessage());
        }
    }

    private void handleBookmark(String action, Long id) {
        if ("DELETE".equals(action)) {
            searchSyncService.deleteBookmark(id);
        } else {
            // CREATE / UPDATE 统一走 upsert（查 MySQL 最新值 → 写 ES）
            searchSyncService.upsertBookmark(id);
        }
    }

    private void handleFolder(String action, Long id) {
        if ("DELETE".equals(action)) {
            searchSyncService.deleteFolder(id);
        } else {
            searchSyncService.upsertFolder(id);
        }
    }
}
