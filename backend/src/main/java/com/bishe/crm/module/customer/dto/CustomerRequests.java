/* 文件作用：集中定义客户档案、联系人、跟进及交接的请求参数。 */
package com.bishe.crm.module.customer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 集中定义客户档案、联系人、跟进及交接的请求参数。 */
public final class CustomerRequests {

    /** 禁止实例化请求参数容器。 */
    private CustomerRequests() {}

    /** 客户基本资料请求参数及校验约束。 */
    public record Profile(
        @NotBlank @Size(max = 160) String customerName,
        @Pattern(regexp = "(?:[0-9A-Za-z]{18})?") String unifiedSocialCreditCode,
        @Size(max = 32) String sourceCode,
        @Size(max = 32) String industryCode,
        @Size(max = 100) String region,
        @Size(max = 32) String companySize,
        @Size(max = 1000) String remark,
        @NotBlank @Size(max = 20) String customerLevel,
        @NotNull @Size(max = 10) List<@NotBlank @Size(max = 64) String> tags
    ) {}

    /** 客户新建请求参数及校验约束。 */
    public record Create(
        @NotNull @Valid Profile profile,
        @NotNull @Valid ContactInput primaryContact,
        @Positive Long ownerId,
        boolean sameNameConfirmed,
        @Size(max = 500) String reviewReason
    ) {}

    /** 客户编辑请求参数及校验约束。 */
    public record Edit(
        @NotNull @PositiveOrZero Long version,
        @NotNull @Valid Profile profile,
        boolean sameNameConfirmed,
        @Size(max = 500) String reviewReason
    ) {}

    /** 客户等级与标签请求参数及校验约束。 */
    public record Classification(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 20) String customerLevel,
        @NotNull @Size(max = 10) List<@NotBlank @Size(max = 64) String> tags
    ) {}

    /** 客户联系人初始资料请求参数及校验约束。 */
    public record ContactInput(
        @NotBlank @Size(max = 80) String contactName,
        @Size(max = 80) String jobTitle,
        @NotBlank @Pattern(regexp = "[+0-9() -]{5,32}") String phone,
        @Email @Size(max = 160) String email,
        @PastOrPresent LocalDate birthday,
        @Pattern(regexp = "CONTACT|DECISION_MAKER|USER|FINANCE|INFLUENCER") String decisionRole,
        boolean primary
    ) {}

    /** 客户联系人写入请求参数及校验约束。 */
    public record ContactWrite(
        @NotNull @PositiveOrZero Long customerVersion,
        @NotNull @Valid ContactInput contact,
        boolean sameNameConfirmed,
        @Size(max = 500) String reviewReason
    ) {}

    /** 客户跟进提交请求参数及校验约束。 */
    public record FollowWrite(
        @NotNull @PositiveOrZero Long customerVersion,
        @Positive Long opportunityId,
        @NotNull @Positive Long contactId,
        @NotBlank @Pattern(regexp = "PHONE|WECHAT|VISIT|EMAIL|OTHER") String followMethod,
        @NotBlank @Size(max = 1000) String content,
        @NotBlank @Pattern(
            regexp = "NEED_CONFIRMED|SOLUTION_DISCUSSED|NEXT_STEP_AGREED|NO_RESPONSE|NOT_CONNECTED|OTHER"
        ) String resultCode,
        @NotBlank @Size(max = 500) String substantiveResult,
        @NotBlank @Size(max = 500) String nextAction,
        @NotNull LocalDateTime nextActionAt,
        @NotNull LocalDateTime followedAt
    ) {}

    /** 客户跟进查验请求参数及校验约束。 */
    public record FollowReview(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Pattern(regexp = "RETURNED|CONFIRMED") String reviewStatus,
        @Size(max = 500) String reviewReason
    ) {}

    /** 客户负责人转交请求参数及校验约束。 */
    public record OwnershipTransfer(
        @NotNull @PositiveOrZero Long version,
        @NotNull @Positive Long newOwnerId,
        @NotBlank @Size(max = 500) String reason
    ) {}

    /** 客户字典新增请求参数及校验约束。 */
    public record DictionaryWrite(
        @NotBlank @Pattern(regexp = "source|industry|tag") String group,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}") String code,
        @NotBlank @Size(max = 40) String label,
        @NotNull @Min(0) @Max(9999) Integer sortNo
    ) {}

    /** 客户字典编辑请求参数及校验约束。 */
    public record DictionaryEdit(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 40) String label,
        @NotNull @Min(0) @Max(9999) Integer sortNo,
        boolean enabled
    ) {}
}
