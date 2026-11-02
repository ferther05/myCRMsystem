/* 文件作用：返回已授权的待办摘要、跳转位置和不含敏感快照的操作记录。 */
package com.bishe.crm.module.task.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 任务中心对外视图。 */
public final class TaskViews {
    private TaskViews() {}
    /** 跳转位置由服务端白名单生成，并在点击时重新检查权限。 */
    public record Target(String path, String label) {}
    /** 业务状态与已读时间分开展示。 */
    public record TodoRow(Long id, String type, String bizType, Long bizId, String title,
        String status, LocalDate remindDate, LocalDateTime createdAt, LocalDateTime readAt,
        LocalDateTime completedAt, LocalDateTime resolvedAt, Long version, Target target) {}
    /** 不返回原始before/after JSON、口令、令牌或附件内容。 */
    public record Operation(Long id, String bizType, Long bizId, String actionCode,
        String operatorName, LocalDateTime occurredAt, String reason) {}
}
