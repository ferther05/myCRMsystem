/* 文件作用：校验账号及所属团队能否使用系统。 */
package com.bishe.crm.module.auth.security;

import com.bishe.crm.module.system.entity.SystemUser;
import java.util.Set;

/** 校验账号及所属团队能否使用系统。 */
public final class AuthUsers {

    private static final Set<String> ROLES = Set.of("ADMIN", "MANAGER", "SPECIALIST");

    /** 禁止实例化账号校验工具类。 */
    private AuthUsers() {}

    /** 检查账号启用、角色合法且所属团队可用。 */
    public static boolean usable(SystemUser user) {
        return (
            user != null &&
            "ACTIVE".equals(user.getAccountStatus()) &&
            user.getRoleCode() != null &&
            ROLES.contains(user.getRoleCode()) &&
            ("ADMIN".equals(user.getRoleCode()) ||
                (user.getTeamId() != null && Boolean.TRUE.equals(user.getTeamEnabled())))
        );
    }
}
