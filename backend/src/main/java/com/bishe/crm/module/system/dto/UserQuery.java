package com.bishe.crm.module.system.dto;

import com.bishe.crm.common.api.PageQuery;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UserQuery extends PageQuery {
    @Size(max=80) private String keyword;
    @Pattern(regexp="ADMIN|MANAGER|SPECIALIST") private String roleCode;
    @Pattern(regexp="ACTIVE|DISABLED|PENDING") private String accountStatus;
}
