/* 文件作用：保存一次扫描尝试及其失败业务主键，供受控补跑。 */
package com.bishe.crm.module.task.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 每次尝试单独落库，不覆盖历史执行结果。 */
@Getter
@Setter
public class JobRun {
    private Long id;
    private String jobName;
    private LocalDate businessDate;
    private String logicalRunId;
    private Integer attemptNo;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String runStatus;
    private Integer successCount;
    private Integer failureCount;
    private String failedBizIdsJson;
    private String errorSummary;
    private Long triggeredBy;
    private String triggeredByName;
    private Boolean retryable;
}
