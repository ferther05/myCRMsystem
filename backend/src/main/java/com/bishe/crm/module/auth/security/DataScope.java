/* 文件作用：根据角色确定数据库查询的数据范围。 */
package com.bishe.crm.module.auth.security;

/** 数据范围由服务端身份生成；Mapper 查询必须同时应用范围、主键及分页条件。 */
public record DataScope(String role, Long userId, Long teamId) {
    /** 按管理员、经理或专员角色生成数据查询范围。 */
    public static DataScope of(AuthPrincipal user) {
        return new DataScope(user.roleCode(), user.id(), user.teamId());
    }
}
