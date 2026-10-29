/* 文件作用：客户列表的数据范围、分类和分页筛选条件。 */
package com.bishe.crm.module.customer.dto;

import com.bishe.crm.common.api.PageQuery;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 客户列表的数据范围、分类和分页筛选条件。 */
@Getter
@Setter
public class CustomerQuery extends PageQuery {

    @Pattern(regexp = "PRIVATE|PUBLIC")
    private String sea = "PRIVATE";

    @Size(max = 160)
    private String keyword;

    @Size(max = 32)
    private String customerStatus;

    @Size(max = 20)
    private String customerLevel;

    @Size(max = 32)
    private String sourceCode;

    @Size(max = 32)
    private String industryCode;

    @Size(max = 100)
    private String region;

    @Positive
    private Long ownerId;

    private boolean opportunityEligible;

    @Size(max = 10)
    private List<@NotBlank @Size(max = 64) String> tags;

    @Pattern(regexp = "ALL|ANY")
    private String tagMatch = "ALL";
}
