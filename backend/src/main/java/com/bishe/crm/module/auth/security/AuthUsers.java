package com.bishe.crm.module.auth.security;

import com.bishe.crm.module.system.entity.SystemUser;
import java.util.Set;

public final class AuthUsers {
    private static final Set<String> ROLES = Set.of("ADMIN", "MANAGER", "SPECIALIST");
    private AuthUsers() { }

    public static boolean usable(SystemUser user) {
        return user != null && "ACTIVE".equals(user.getAccountStatus())
                && user.getRoleCode() != null && ROLES.contains(user.getRoleCode())
                && ("ADMIN".equals(user.getRoleCode())
                    || (user.getTeamId() != null && Boolean.TRUE.equals(user.getTeamEnabled())));
    }
}
