/* 文件作用：按当前范围和历史快照分别聚合看板，避免多表JOIN造成金额重复累计。 */
package com.bishe.crm.module.analytics.mapper;

import com.bishe.crm.module.analytics.vo.DashboardViews;
import com.bishe.crm.module.auth.security.DataScope;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DashboardMapper {
    /** 管理员当前资源与待核验摘要，子查询分别聚合避免JOIN重复。 */
    @Select("SELECT " +
        "(SELECT COUNT(*) FROM crm_customer WHERE deleted=0 AND sea_type='PUBLIC') publicCustomers," +
        "(SELECT COUNT(*) FROM crm_customer WHERE deleted=0 AND sea_type='PUBLIC' AND owner_id IS NULL) unassignedPublicCustomers," +
        "(SELECT COUNT(*) FROM sys_user WHERE account_status='ACTIVE') activeAccounts," +
        "(SELECT COUNT(*) FROM sys_user WHERE account_status='DISABLED') disabledAccounts," +
        "(SELECT COUNT(*) FROM sys_user WHERE account_status='PENDING') pendingAccounts," +
        "(SELECT COUNT(*) FROM sys_team WHERE enabled=1) enabledTeams," +
        "(SELECT COUNT(*) FROM crm_approval a JOIN sys_user u ON u.id=a.biz_id WHERE a.approval_type='ACCOUNT_OPEN' AND a.biz_type='USER' AND a.approval_status='PENDING' AND u.account_status='PENDING') pendingAccountApplications," +
        "(SELECT COUNT(*) FROM crm_contract WHERE contract_status='PENDING_REVIEW' AND signing_progress IN ('DRAFT_REVIEW','FILE_REVIEW')) pendingContractReviews")
    Map<String,Object> adminResourceCounts();

    /** 启用及停用团队均可查看资源残留；在办商机不含终态。 */
    @Select("SELECT t.id,t.team_name," +
        "(SELECT COUNT(*) FROM sys_user u WHERE u.team_id=t.id AND u.account_status='ACTIVE') active_members," +
        "(SELECT COUNT(*) FROM crm_customer c WHERE c.team_id=t.id AND c.deleted=0 AND c.sea_type='PRIVATE') private_customers," +
        "(SELECT COUNT(*) FROM crm_opportunity o JOIN crm_customer c ON c.id=o.customer_id WHERE c.deleted=0 AND c.sea_type='PRIVATE' AND o.team_id=t.id AND o.stage_code IN ('NEW','FOLLOWING','QUOTED','DEAL')) open_opportunities " +
        "FROM sys_team t ORDER BY t.enabled DESC,t.id")
    List<DashboardViews.TeamResources> adminTeamResources();

    /** 只返回本人、本团队或管理员范围内的筛选人员，包含历史停用人员。 */
    List<DashboardViews.Owner> owners(@Param("s") DataScope scope);
    /** 当前存量与时间范围内的业绩分别计算。 */
    Map<String,Object> metrics(@Param("s") DataScope scope,@Param("owner") Long owner,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end,@Param("today") LocalDate today);
    /** 当前阶段分布，按商机创建时间筛选。 */
    List<Map<String,Object>> stages(@Param("s") DataScope scope,@Param("owner") Long owner,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
    /** 签约与到账分别先聚合再组合，不互相乘倍。 */
    List<Map<String,Object>> trends(@Param("s") DataScope scope,@Param("owner") Long owner,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
    /** 排行按历史业绩人员聚合，顺序由服务端固定。 */
    List<Map<String,Object>> ranking(@Param("s") DataScope scope,@Param("owner") Long owner,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
    /** 同一创建批次的历史到达成交、赢单记录，不用当前阶段数量冒充转化。 */
    Map<String,Object> conversion(@Param("s") DataScope scope,@Param("owner") Long owner,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
    /** 当前阶段停留时长与筛选期内回退次数，复用同一权限范围。 */
    List<Map<String,Object>> stageDurations(@Param("s") DataScope scope,@Param("owner") Long owner,@Param("now") LocalDateTime now);
    long rollbackCount(@Param("s") DataScope scope,@Param("owner") Long owner,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
    /** 本人当前待办始终实时查询，不把管理员待办分享给其他角色。 */
    List<Map<String,Object>> actions(Long actor);
}
