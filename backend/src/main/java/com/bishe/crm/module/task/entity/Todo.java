/* 文件作用：保存待办的接收人、业务状态、已读时间及并发版本。 */
package com.bishe.crm.module.task.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 原业务维护状态，任务中心只维护接收人的已读标记。 */
@Getter
@Setter
public class Todo {
    private Long id;
    private String todoType;
    private String bizType;
    private Long bizId;
    private Long recipientId;
    private String title;
    private String todoStatus;
    private LocalDate remindDate;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private LocalDateTime completedAt;
    private LocalDateTime resolvedAt;
    private Long version;
}
