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
        @NotBlank(message = "请填写合同名称") @Size(
            max = 160,
            message = "合同名称最多160字"
        ) String contractName,
        @Size(max = 64) String externalContractNo,
        @Size(max = 64) String businessType,
        @Size(max = 160) String ourPartyName,
        @Size(max = 160) String counterpartyName,
        @NotNull @PositiveOrZero @Digits(integer = 16, fraction = 2) BigDecimal contractAmount,
        LocalDate signedAt,
        LocalDate expireDate,
        @Size(max = 64) String ourContactName,
        @Size(max = 40, message = "我方联系电话最多40位") @Pattern(
            regexp = "^$|[0-9+() -]{3,40}",
            message = "我方联系电话需为3～40位数字、空格、加减号或括号"
        ) String ourContactPhone,
        @Size(max = 64) String counterpartyContactName,
        @Size(max = 40, message = "对方联系电话最多40位") @Pattern(
            regexp = "^$|[0-9+() -]{3,40}",
            message = "对方联系电话需为3～40位数字、空格、加减号或括号"
        ) String counterpartyContactPhone,
        @Size(max = 255) String ourAddress,
        @Size(max = 255) String counterpartyAddress,
        @Size(max = 18, message = "我方统一社会信用代码须为18位大写字母或数字，也可留空") @Pattern(
            regexp = "^$|[0-9A-Z]{18}",
            message = "我方统一社会信用代码须为18位大写字母或数字，也可留空"
        ) String ourCreditCode,
        @Size(max = 18, message = "对方统一社会信用代码须为18位大写字母或数字，也可留空") @Pattern(
            regexp = "^$|[0-9A-Z]{18}",
            message = "对方统一社会信用代码须为18位大写字母或数字，也可留空"
        ) String counterpartyCreditCode,
        @Size(max = 1000) String paymentTerms,
        @Size(max = 1000) String deliveryTerms,
        LocalDate plannedSignedAt,
        LocalDate performanceStartDate,
        @Size(max = 1000) String description
    ) {}

    /** 合同提交核验请求参数及校验约束。 */
    public record Submit(@NotNull @PositiveOrZero Long version) {}

    /** 负责人撤回待审登记，重新开放表单编辑。 */
    public record WithdrawDraft(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 500) String reason
    ) {}

    /** 合同审核请求参数及校验约束。 */
    public record Review(
        @NotNull @PositiveOrZero Long version,
        boolean pass,
        @NotBlank @Size(max = 1000) String opinion
    ) {}

    /** 合同更新签署节点请求参数及校验约束。 */
    public record SigningProgress(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Pattern(regexp = "EXCEPTION|WITHDRAWN|NORMAL") String targetProgress,
        @NotBlank @Size(max = 500) String reason
    ) {}
}
