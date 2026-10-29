/* 文件作用：认证账号查询、登录记录、密码和会话版本的数据访问。 */
package com.bishe.crm.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bishe.crm.module.system.entity.SystemUser;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 认证账号查询、登录记录、密码和会话版本的数据访问。 */
@Mapper
public interface SystemUserMapper extends BaseMapper<SystemUser> {
    String USER_SELECT =
        "SELECT u.id, u.username, u.password_hash, u.real_name, u.phone, u.email, u.role_code, " +
        "u.team_id, u.account_status, u.must_change_password, u.claim_date, u.claim_count, u.current_hold_count, u.last_login_at, u.version, " +
        "u.created_at, u.updated_at, t.team_name, t.enabled AS team_enabled " +
        "FROM sys_user u LEFT JOIN sys_team t ON t.id=u.team_id ";

    /** 按登录账号查询用户及团队状态。 */
    @Select(USER_SELECT + "WHERE u.username=#{username}")
    SystemUser findByUsername(String username);

    /** 按用户主键查询最新身份与团队状态。 */
    @Select(USER_SELECT + "WHERE u.id=#{id}")
    SystemUser findWithTeamById(Long id);

    /** 记录登录时间并更新会话版本。 */
    @Update(
        "UPDATE sys_user SET last_login_at=#{now}, updated_at=#{now}, version=version+1 " +
            "WHERE id=#{id} AND version=#{version} AND password_hash=#{passwordHash} " +
            "AND account_status='ACTIVE'"
    )
    int recordLogin(
        @Param("id") Long id,
        @Param("version") Long version,
        @Param("passwordHash") String passwordHash,
        @Param("now") LocalDateTime now
    );

    /** 按版本更新密码、首次改密标记及会话版本。 */
    @Update(
        "UPDATE sys_user SET password_hash=#{newHash}, must_change_password=0, " +
            "version=version+1, updated_at=#{now} WHERE id=#{id} AND version=#{version} " +
            "AND password_hash=#{oldHash} AND account_status='ACTIVE'"
    )
    int changePassword(
        @Param("id") Long id,
        @Param("version") Long version,
        @Param("oldHash") String oldHash,
        @Param("newHash") String newHash,
        @Param("now") LocalDateTime now
    );

    /** 提升账号会话版本使旧令牌失效。 */
    @Update(
        "UPDATE sys_user SET version=version+1, updated_at=#{now} " +
            "WHERE id=#{id} AND version=#{version} AND account_status='ACTIVE'"
    )
    int revokeSession(
        @Param("id") Long id,
        @Param("version") Long version,
        @Param("now") LocalDateTime now
    );

    /** 写入密码修改成功审计，保留事务一致性。 */
    @Insert(
        "INSERT INTO crm_operation_log (biz_type, biz_id, action_code, operator_id, " +
            "operator_team_id, occurred_at, reason) VALUES ('USER', #{id}, 'PASSWORD_CHANGED', " +
            "#{id}, #{teamId}, #{now}, '用户自行修改密码，旧会话已失效')"
    )
    int insertPasswordAudit(
        @Param("id") Long id,
        @Param("teamId") Long teamId,
        @Param("now") LocalDateTime now
    );
}
