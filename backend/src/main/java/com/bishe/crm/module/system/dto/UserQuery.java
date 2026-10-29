/* 文件作用：账号列表的分页与筛选条件。 */
package com.bishe.crm.module.system.dto;

import com.bishe.crm.common.api.PageQuery;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 账号列表的分页与筛选条件。 */
@Getter
@Setter
public class UserQuery extends PageQuery {

    @Size(max = 80)
    private String keyword;

    @Pattern(regexp = "ADMIN|MANAGER|SPECIALIST")
    private String roleCode;

    @Pattern(regexp = "ACTIVE|DISABLED|PENDING")
    private String accountStatus;
}
