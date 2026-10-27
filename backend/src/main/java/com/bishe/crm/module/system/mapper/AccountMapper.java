package com.bishe.crm.module.system.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bishe.crm.module.auth.security.DataScope;
import com.bishe.crm.module.system.dto.UserQuery;
import com.bishe.crm.module.system.entity.AccountApplication;
import com.bishe.crm.module.system.entity.SystemUser;
import com.bishe.crm.module.system.vo.TeamVO;
import com.bishe.crm.module.system.vo.UserVO;
import com.bishe.crm.module.system.vo.ApplicationVO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountMapper {
    String USER_FIELDS = "SELECT u.id,u.username,u.real_name,u.phone,u.email,u.role_code,u.team_id,"
            + "t.team_name,u.account_status,u.must_change_password,u.version,u.last_login_at "
            + "FROM sys_user u LEFT JOIN sys_team t ON t.id=u.team_id ";
    String USER_SCOPE = "<choose><when test=\"s.role == 'ADMIN'\">1=1</when>"
            + "<when test=\"s.role == 'MANAGER'\">u.team_id=#{s.teamId}</when>"
            + "<otherwise>u.id=#{s.userId}</otherwise></choose>";

    @Select("<script>" + USER_FIELDS + "WHERE " + USER_SCOPE
            + "<if test='q.keyword != null and q.keyword != &quot;&quot;'> AND (u.username LIKE CONCAT('%',#{q.keyword},'%') OR u.real_name LIKE CONCAT('%',#{q.keyword},'%'))</if>"
            + "<if test='q.roleCode != null'> AND u.role_code=#{q.roleCode}</if>"
            + "<if test='q.accountStatus != null'> AND u.account_status=#{q.accountStatus}</if>"
            + " ORDER BY u.id</script>")
    IPage<UserVO> list(Page<UserVO> page, @Param("s") DataScope scope, @Param("q") UserQuery query);

    @Select("<script>" + USER_FIELDS + "WHERE u.id=#{id} AND " + USER_SCOPE + "</script>")
    UserVO detail(@Param("id") Long id, @Param("s") DataScope scope);

    @Select(SystemUserMapper.USER_SELECT + "WHERE u.id=#{id} FOR UPDATE")
    SystemUser lockUser(Long id);

    @Select("SELECT id FROM sys_user WHERE role_code='ADMIN' ORDER BY id FOR UPDATE")
    List<Long> lockAdministrators();

    @Select("SELECT id FROM sys_team WHERE id=#{id} AND enabled=1 FOR UPDATE")
    Long lockEnabledTeam(Long id);

    @Select("SELECT COUNT(*) FROM sys_user WHERE role_code='ADMIN' AND account_status='ACTIVE'")
    long activeAdministrators();

    @Insert("INSERT INTO sys_user(username,password_hash,real_name,phone,email,role_code,team_id,account_status,must_change_password) "
            + "VALUES(#{username},#{passwordHash},#{realName},#{phone},#{email},#{roleCode},#{teamId},#{accountStatus},1)")
    @Options(useGeneratedKeys=true, keyProperty="id")
    int create(SystemUser user);

    @Update("UPDATE sys_user SET real_name=#{name},phone=#{phone},email=#{email},version=version+1,updated_at=#{now} "
            + "WHERE id=#{id} AND version=#{version} AND account_status='ACTIVE'")
    int profile(@Param("id") Long id, @Param("version") long version, @Param("name") String name,
                @Param("phone") String phone, @Param("email") String email, @Param("now") LocalDateTime now);

    @Update("UPDATE sys_user SET role_code=#{role},team_id=#{team},account_status=#{status},version=version+1,updated_at=#{now} "
            + "WHERE id=#{id} AND version=#{version} AND account_status!='PENDING'")
    int access(@Param("id") Long id, @Param("version") long version, @Param("role") String role,
               @Param("team") Long team, @Param("status") String status, @Param("now") LocalDateTime now);

    @Update("UPDATE sys_user SET password_hash=#{hash},must_change_password=1,version=version+1,updated_at=#{now} "
            + "WHERE id=#{id} AND version=#{version} AND account_status!='PENDING'")
    int reset(@Param("id") Long id, @Param("version") long version, @Param("hash") String hash,
              @Param("now") LocalDateTime now);

    @Update("UPDATE sys_team SET manager_id=NULL,version=version+1 WHERE manager_id=#{id}")
    int clearManager(Long id);

    @Update("UPDATE sys_team SET manager_id=#{user},version=version+1 WHERE id=#{team} AND manager_id IS NULL")
    int assignVacantManager(@Param("team") Long team, @Param("user") Long user);

    @Select("SELECT (SELECT COUNT(*) FROM crm_customer WHERE owner_id=#{id} AND deleted=0) "
            + "+(SELECT COUNT(*) FROM crm_opportunity WHERE owner_id=#{id}) "
            + "+(SELECT COUNT(*) FROM crm_contract WHERE owner_id=#{id}) "
            + "+(SELECT COUNT(*) FROM crm_opportunity_collaborator WHERE user_id=#{id} AND collaborator_status='ACTIVE')")
    long businessLinks(Long id);

    @Insert("INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,operator_team_id,occurred_at,reason) "
            + "VALUES('USER',#{target},#{action},#{actor},#{team},#{now},#{reason})")
    int audit(@Param("target") Long target, @Param("action") String action, @Param("actor") Long actor,
              @Param("team") Long team, @Param("now") LocalDateTime now, @Param("reason") String reason);

    @Update("UPDATE sys_user SET claim_date=#{date},claim_count=IF(claim_date=#{date},claim_count+IF(#{priority},0,1),IF(#{priority},0,1)) WHERE id=#{id}")
    int recordClaim(@Param("id") Long id,@Param("date") java.time.LocalDate date,@Param("priority") boolean priority);

    @Update("UPDATE sys_user SET current_hold_count=#{count} WHERE id=#{id}")
    int syncHoldCount(@Param("id") Long id,@Param("count") int count);

    @Select("<script>SELECT t.id,t.team_code,t.team_name,t.manager_id,m.real_name AS manager_name,t.enabled "
            + "FROM sys_team t LEFT JOIN sys_user m ON m.id=t.manager_id "
            + "WHERE <choose><when test=\"s.role == 'ADMIN'\">1=1</when>"
            + "<otherwise>t.id=#{s.teamId}</otherwise></choose> ORDER BY t.id</script>")
    List<TeamVO> teams(@Param("s") DataScope scope);

    @Insert("INSERT INTO crm_approval(approval_type,biz_type,biz_id,applicant_id,reason) "
            + "VALUES('ACCOUNT_OPEN','USER',#{bizId},#{applicantId},#{reason})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int apply(AccountApplication application);

    String APPLICATION_FIELDS = "SELECT a.id,a.biz_id,a.applicant_id,a.approver_id,a.approval_status,a.reason,"
            + "JSON_UNQUOTE(JSON_EXTRACT(a.extra_json,'$.reviewOpinion')) AS review_opinion,a.version,"
            + "u.username,u.real_name,u.team_id,t.team_name,p.real_name AS applicant_name,a.submitted_at,a.processed_at "
            + "FROM crm_approval a JOIN sys_user u ON u.id=a.biz_id JOIN sys_user p ON p.id=a.applicant_id "
            + "LEFT JOIN sys_team t ON t.id=u.team_id WHERE a.approval_type='ACCOUNT_OPEN' AND a.biz_type='USER' ";

    @Select("<script>" + APPLICATION_FIELDS
            + "<if test=\"s.role != 'ADMIN'\"> AND a.applicant_id=#{s.userId} AND u.team_id=#{s.teamId}</if>"
            + " ORDER BY a.id DESC</script>")
    IPage<ApplicationVO> applications(Page<ApplicationVO> page, @Param("s") DataScope scope);

    @Select(APPLICATION_FIELDS + "AND a.id=#{id} FOR UPDATE")
    AccountApplication lockApplication(Long id);

    @Update("UPDATE crm_approval SET approval_status=#{status},approver_id=#{actor},processed_at=#{now},"
            + "extra_json=JSON_OBJECT('reviewOpinion',#{opinion}),version=version+1 "
            + "WHERE id=#{id} AND version=#{version} AND approval_status='PENDING'")
    int review(@Param("id") Long id, @Param("version") long version, @Param("status") String status,
               @Param("actor") Long actor, @Param("now") LocalDateTime now, @Param("opinion") String opinion);

    @Update("UPDATE sys_user SET account_status='ACTIVE',password_hash=#{hash},must_change_password=1,"
            + "version=version+1,updated_at=#{now} WHERE id=#{id} AND account_status='PENDING'")
    int activate(@Param("id") Long id, @Param("hash") String hash, @Param("now") LocalDateTime now);

    @Update("UPDATE crm_approval SET approval_status='PENDING',approver_id=NULL,processed_at=NULL,extra_json=NULL,"
            + "version=version+1,submitted_at=#{now} WHERE id=#{id} AND version=#{version} AND approval_status='REJECTED'")
    int resubmit(@Param("id") Long id, @Param("version") long version, @Param("now") LocalDateTime now);
}
