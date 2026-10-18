package com.bishe.crm.module.auth.security;

/** Server-derived scope only. Mapper queries must use this alongside object ids and pagination. */
public record DataScope(String role, Long userId, Long teamId) {
    public static DataScope of(AuthPrincipal user) {
        return new DataScope(user.roleCode(), user.id(), user.teamId());
    }
}
