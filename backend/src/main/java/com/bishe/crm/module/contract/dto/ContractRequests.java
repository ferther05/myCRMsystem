/* 文件作用：集中定义合同各项操作的请求参数。 */
package com.bishe.crm.module.contract.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 集中定义合同各项操作的请求参数。 */
public final class ContractRequests {

    /** 禁止实例化请求参数容器。 */
    private ContractRequests() {}

    /** 合同新建请求参数及校验约束。 */
    public record Create(@NotNull @Positive Long opportunityId) {}

    /** 合同编辑请求参数及校验约束。 */
    public record Edit(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 160) String contractName,
        @Size(max = 64) String externalContractNo,
        @Size(max = 64) String businessType,
        @Size(max = 160) String ourPartyName,
        @Size(max = 160) String counterpartyName,
        @NotNull @PositiveOrZero @Digits(integer = 16, fraction = 2) BigDecimal contractAmount,
        LocalDate signedAt,
        LocalDate expireDate,
        @Size(max = 1000) String description
    ) {}

    /** 合同提交核验请求参数及校验约束。 */
    public record Submit(@NotNull @PositiveOrZero Long version) {}

    /** 合同审核请求参数及校验约束。 */
    public record Review(
        @NotNull @PositiveOrZero Long version,
        boolean pass,
        @NotBlank @Size(max = 1000) String opinion
    ) {}

    /** 合同更新签署节点请求参数及校验约束。 */
    public record SigningProgress(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Pattern(
            regexp = "INTERNAL_HANDLING|WAIT_OUR_SIGN|WAIT_COUNTERPARTY_SIGN|BOTH_SIGNED|EXCEPTION|WITHDRAWN"
        ) String targetProgress,
        @NotBlank @Size(max = 500) String reason
    ) {}
}
