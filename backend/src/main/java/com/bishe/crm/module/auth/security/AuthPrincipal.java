package com.bishe.crm.module.auth.security;

import com.bishe.crm.module.system.entity.SystemUser;

public record AuthPrincipal(Long id, String username, String realName, String roleCode,
                            Long teamId, String teamName, boolean mustChangePassword, long version) {
    public static AuthPrincipal from(SystemUser user) {
        return new AuthPrincipal(user.getId(), user.getUsername(), user.getRealName(), user.getRoleCode(),
                user.getTeamId(), user.getTeamName(), Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getVersion());
    }
}
