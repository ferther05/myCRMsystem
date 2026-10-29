/* 文件作用：请求中已认证用户的身份和权限信息。 */
package com.bishe.crm.module.auth.security;

import com.bishe.crm.module.system.entity.SystemUser;

/** 请求中已认证用户的身份和权限信息。 */
public record AuthPrincipal(
    Long id,
    String username,
    String realName,
    String roleCode,
    Long teamId,
    String teamName,
    boolean mustChangePassword,
    long version
) {
    /** 从数据库账号创建当前请求的认证身份。 */
    public static AuthPrincipal from(SystemUser user) {
        return new AuthPrincipal(
            user.getId(),
            user.getUsername(),
            user.getRealName(),
            user.getRoleCode(),
            user.getTeamId(),
            user.getTeamName(),
            Boolean.TRUE.equals(user.getMustChangePassword()),
            user.getVersion()
        );
    }
}
