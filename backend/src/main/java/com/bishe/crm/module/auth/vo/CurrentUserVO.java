package com.bishe.crm.module.auth.vo;

import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.system.entity.SystemUser;

public record CurrentUserVO(Long id, String username, String realName, String roleCode,
                            Long teamId, String teamName, boolean mustChangePassword, String phone, String email) {
    public static CurrentUserVO from(AuthPrincipal user) {
        return new CurrentUserVO(user.id(), user.username(), user.realName(), user.roleCode(),
                user.teamId(), user.teamName(), user.mustChangePassword(), null, null);
    }

    public static CurrentUserVO from(SystemUser user) {
        return new CurrentUserVO(user.getId(), user.getUsername(), user.getRealName(), user.getRoleCode(),
                user.getTeamId(), user.getTeamName(), Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getPhone(), user.getEmail());
    }
}
