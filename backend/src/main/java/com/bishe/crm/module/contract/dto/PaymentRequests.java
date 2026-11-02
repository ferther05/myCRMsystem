/* 文件作用：定义回款计划、到账和合同终止操作的请求参数。 */
package com.bishe.crm.module.contract.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 回款操作请求及其校验条件。 */
public final class PaymentRequests {
    private PaymentRequests() {}

    /** 新建或修订一版计划；期次金额由服务端汇总校验。 */
    public record Plan(
        @NotNull @PositiveOrZero Long version,
        @Size(max = 1000) String changeDescription,
        @NotEmpty @Size(max = 24) List<@Valid Period> periods
    ) {}

    /** 单个回款期次。 */
    public record Period(
        @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal plannedAmount,
        @NotNull LocalDate dueDate,
        @NotBlank @Pattern(regexp = "SIGNING|DELIVERY|ACCEPTANCE|WARRANTY|OTHER") String triggerNode
    ) {}

    /** 提交待审核计划。 */
    public record Submit(@NotNull @PositiveOrZero Long version) {}

    /** 审批通过或退回计划。 */
    public record Review(
        @NotNull @PositiveOrZero Long version,
        boolean pass,
        @NotBlank @Size(max = 1000) String opinion
    ) {}

    /** 登记一笔实际到账，重复请求以幂等键拒绝重复入账。 */
    public record Receipt(
        @NotNull @Positive Long periodId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @NotNull LocalDateTime receivedAt,
        @Pattern(regexp = "^$|BANK_TRANSFER|CASH|ONLINE|OTHER") String paymentMethod,
        @Positive Long voucherAttachmentId,
        @Size(max = 500) String remark,
        @Size(max = 500) String differenceDescription,
        @NotBlank @Size(max = 100) String idempotencyKey
    ) {}

    /** 管理员作废错误到账记录，不物理删除原记录。 */
    public record VoidReceipt(
        @NotBlank @Size(max = 500) String reason,
        @NotNull @PositiveOrZero Long version
    ) {}

    /** 发起“终止且无应收”审批。 */
    public record Termination(
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Size(max = 1000) String noReceivableExplanation
    ) {}
}
