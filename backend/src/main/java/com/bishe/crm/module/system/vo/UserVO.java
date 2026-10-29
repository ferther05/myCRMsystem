/* 文件作用：返回账号资料及可见权限信息。 */
package com.bishe.crm.module.system.vo;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 返回账号资料及可见权限信息。 */
@Getter
@Setter
public class UserVO {

    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String roleCode;
    private Long teamId;
    private String teamName;
    private String accountStatus;
    private Boolean mustChangePassword;
    private Long version;
    private LocalDateTime lastLoginAt;
}
