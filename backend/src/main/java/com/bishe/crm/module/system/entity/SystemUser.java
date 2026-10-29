/* 文件作用：系统账号实体，保存角色、团队和会话版本。 */
package com.bishe.crm.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bishe.crm.common.entity.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 系统账号实体，保存角色、团队和会话版本。 */
@Getter
@Setter
@TableName("sys_user")
public class SystemUser extends BaseEntity {

    private String username;
    private String passwordHash;
    private String realName;
    private String phone;
    private String email;
    private String roleCode;
    private Long teamId;
    private String accountStatus;
    private Boolean mustChangePassword;
    private LocalDate claimDate;
    private Integer claimCount;
    private Integer currentHoldCount;
    private LocalDateTime lastLoginAt;

    @TableField(exist = false)
    private String teamName;

    @TableField(exist = false)
    private Boolean teamEnabled;
}
