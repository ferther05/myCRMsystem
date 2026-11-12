/* 文件作用：工作台分类与固定十条分页参数。 */
package com.bishe.crm.module.workbench.dto;

import com.bishe.crm.common.api.PageQuery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkbenchQuery extends PageQuery {
    @NotBlank
    @Pattern(regexp="TODAY|FOLLOW_DUE|CUSTOMER_WARNING|RETURNED|OPEN|REVIEW|FOLLOW_REVIEW|CONTRACT_REVIEW|APPROVAL|STAGNANT|OVERDUE|JOB_FAILURE|ACCOUNTS")
    private String category;
}
