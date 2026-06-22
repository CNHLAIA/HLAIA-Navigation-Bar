package com.hlaia.service;

import com.hlaia.entity.OperationLog;
import com.hlaia.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 【操作日志服务】—— 把操作日志异步写入数据库
 *
 * 取代原 Kafka 链路（OperationLogAspect → KafkaProducer → operation-log Topic → OperationLogConsumer）。
 * 现在：OperationLogAspect → 本服务 record()（@Async 虚拟线程） → 直接 insert operation_log 表。
 *
 * 为什么 @Async 而不是直接同步 insert？
 *   日志属于"非关键路径"——日志写入失败不应拖慢或影响业务请求。
 *   异步化让请求线程立即返回，DB 写入在虚拟线程后台完成。
 *   日志失败时本方法内部已兜底（见下方 catch），不会冒泡到 Aspect。
 *
 * 为什么不再经过 Kafka？
 *   原 Kafka 消费者最终也是写同一个 MySQL，绕一圈消息队列反而增加了网络跳、
 *   JSON 序列化和 MQ 持久化开销。单体应用直接异步写库最简单可靠。
 *
 * @Async 自调用陷阱：
 *   Spring 的 @Async 通过代理生效，必须由"外部调用者"进入才会异步执行。
 *   本服务被 OperationLogAspect（另一个 Bean）调用，天然走代理，无此问题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    /**
     * 异步记录一条操作日志
     *
     * @param userId 操作用户 ID（部分场景如系统自动操作可能为 null）
     * @param action 操作类型，大写下划线常量（如 "BAN_USER"、"DELETE_FOLDER"），便于 SQL 检索
     * @param target 操作定位（如 "AdminController.banUser"），便于反查代码位置
     * @param detail 业务上下文（如 "banned user 5"），可为 null
     */
    @Async
    public void record(Long userId, String action, String target, String detail) {
        try {
            OperationLog logEntry = new OperationLog();
            logEntry.setUserId(userId);
            logEntry.setAction(action);
            logEntry.setTarget(target);
            logEntry.setDetail(detail);
            // 使用服务器当前时间，而非请求时间——异步方法可能因线程调度延迟执行
            logEntry.setCreatedAt(LocalDateTime.now());
            operationLogMapper.insert(logEntry);
        } catch (Exception e) {
            // 兜底：日志写入失败绝不能影响业务。仅记录 warn，便于运维排查
            log.warn("Failed to save operation log (action={}, target={}): {}", action, target, e.getMessage());
        }
    }
}
