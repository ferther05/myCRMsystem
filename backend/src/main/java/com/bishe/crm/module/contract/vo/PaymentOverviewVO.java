/* 文件作用：返回合同回款汇总、计划版本、期次、到账与操作权限。 */
package com.bishe.crm.module.contract.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 合同回款详情。 */
public record PaymentOverviewVO(
    Long contractId,
    String paymentStatus,
    BigDecimal contractAmount,
    BigDecimal receivedAmount,
    BigDecimal outstandingAmount,
    List<Plan> plans,
    List<Receipt> receipts,
    Termination termination,
    boolean canManagePlan,
    boolean canReviewPlan,
    boolean canRegisterReceipt,
    boolean canVoidReceipt,
    boolean canRequestTermination,
    boolean canReviewTermination
) {
    /** 一版计划与其分期。 */
    public record Plan(
        Long id,
        Integer versionNo,
        BigDecimal totalPlannedAmount,
        String changeDescription,
        String approvalStatus,
        boolean current,
        LocalDateTime submittedAt,
        String submitterName,
        LocalDateTime approvedAt,
        String approverName,
        Long version,
        List<Period> periods
    ) {}

    /** 计划中的单个期次。 */
    public record Period(
        Long id,
        Integer periodNo,
        BigDecimal plannedAmount,
        BigDecimal receivedAmount,
        BigDecimal outstandingAmount,
        LocalDate dueDate,
        String triggerNode,
        String periodStatus,
        boolean locked,
        Long replacedByPlanId
    ) {}

    /** 实际到账记录。 */
    public record Receipt(
        Long id,
        Long periodId,
        BigDecimal amount,
        LocalDateTime receivedAt,
        String paymentMethod,
        Long voucherAttachmentId,
        String remark,
        String differenceDescription,
        String registeredByName,
        LocalDateTime createdAt,
        boolean voided,
        String voidReason,
        String voidedByName,
        LocalDateTime voidedAt,
        Long version
    ) {}

    /** 终止申请及审核状态。 */
    public record Termination(
        Long approvalId,
        String status,
        String reason,
        String noReceivableExplanation,
        String applicantName,
        LocalDateTime submittedAt,
        String approverName,
        String opinion,
        LocalDateTime processedAt,
        Long version
    ) {}
}
