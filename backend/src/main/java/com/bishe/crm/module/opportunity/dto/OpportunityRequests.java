/* 文件作用：集中定义商机及协作操作的请求参数。 */
package com.bishe.crm.module.opportunity.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 集中定义商机及协作操作的请求参数。 */
public final class OpportunityRequests {

    /** 禁止实例化请求参数容器。 */
    private OpportunityRequests() {}

    /** 商机新建请求参数及校验约束。 */
    public record Create(
        @NotNull @Positive Long customerId,
        @NotBlank @Size(max = 160) String opportunityName,
        @PositiveOrZero @NotNull @Digits(integer = 16, fraction = 2) BigDecimal expectedAmount,
        @NotNull LocalDate expectedCloseDate,
        @Positive Long ownerId
    ) {}

    /** 商机编辑请求参数及校验约束。 */
    public record Edit(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 160) String opportunityName,
        @PositiveOrZero @NotNull @Digits(integer = 16, fraction = 2) BigDecimal expectedAmount,
        @NotNull LocalDate expectedCloseDate
    ) {}

    /** 商机报价或合作确认证据请求参数及校验约束。 */
    public record Evidence(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Pattern(regexp = "QUOTE|CONFIRMATION") String kind,
        @PositiveOrZero @Digits(integer = 16, fraction = 2) BigDecimal quoteAmount,
        @NotBlank @Size(max = 500) String description
    ) {}

    /** 商机待关闭裁决请求参数及校验约束。 */
    public record PendingDecision(
        @NotNull @PositiveOrZero Long version,
        boolean close,
        @NotBlank @Size(max = 500) String reason
    ) {}

    /** 商机管理员阶段修正请求参数及校验约束。 */
    public record StageAction(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Pattern(regexp = "NEW|FOLLOWING|QUOTED|DEAL|WON|LOST|CLOSED") String targetStage,
        @Size(max = 500) String reason
    ) {}

    /** 商机当前商机阈值请求参数及校验约束。 */
    public record Threshold(
        @NotNull @PositiveOrZero Long version,
        @NotNull @Min(1) @Max(3650) Integer stagnationDays,
        @NotNull @Min(0) @Max(365) Integer graceDays
    ) {}

    /** 商机添加协作人请求参数及校验约束。 */
    public record CollaboratorWrite(
        @NotNull @PositiveOrZero Long version,
        @NotNull @Positive Long userId,
        @NotNull @DecimalMin("0.01") @DecimalMax("30.00") @Digits(
            integer = 2,
            fraction = 2
        ) BigDecimal splitRatio
    ) {}

    /** 商机移除协作人请求参数及校验约束。 */
    public record CollaboratorRemove(
        @NotNull @PositiveOrZero Long version,
        @NotNull @Positive Long collaboratorId
    ) {}
}
