/* 文件作用：映射一版合同回款计划。 */
package com.bishe.crm.module.contract.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 回款计划版本实体。 */
@Data
public class PaymentPlan {
    private Long id;
    private Long contractId;
    private Integer versionNo;
    private BigDecimal totalPlannedAmount;
    private String changeDescription;
    private String approvalStatus;
    private Long approvalId;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime effectiveAt;
    private Boolean isCurrent;
    private Long version;
    private Long createdBy;
    private LocalDateTime createdAt;
}
