/* 文件作用：待办、操作记录及执行日志的分页筛选参数。 */
package com.bishe.crm.module.task.dto;

import com.bishe.crm.common.api.PageQuery;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/** 所有页签固定每页十条，日期包含首尾两日。 */
@Getter
@Setter
public class TaskQuery extends PageQuery {
    @Pattern(regexp = "PENDING|DONE|RESOLVED|CANCELLED")
    private String status;
    @Pattern(regexp = "OPP_STAGNANT|OPP_CLOSE_PENDING|OPP_ESCALATED|PAYMENT_OVERDUE|APPROVAL|CUSTOMER_WARNING|FOLLOW_DUE|CONTRACT_STAGNANT|CARE_BIRTHDAY|CONTRACT_REVIEW")
    private String type;
    private Boolean unreadOnly;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
    @Size(max = 64)
    private String action;
    @Pattern(regexp = "CUSTOMER|OPPORTUNITY|CONTRACT|USER|TODO|JOB|SYSTEM_CONFIG")
    private String bizType;
    @jakarta.validation.constraints.Positive
    private Long bizId;
    @Pattern(regexp = "opportunity-activity-scan|payment-overdue-scan|customer-recycle-scan|business-reminder-scan")
    private String jobName;
    @Pattern(regexp = "RUNNING|SUCCESS|PARTIAL_FAILURE|FAILED")
    private String runStatus;
}
