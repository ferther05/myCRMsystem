/* 文件作用：客户档案、归属、跟进计时与审计的数据访问。 */
package com.bishe.crm.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bishe.crm.module.auth.security.DataScope;
import com.bishe.crm.module.customer.dto.CustomerQuery;
import com.bishe.crm.module.customer.entity.Customer;
import com.bishe.crm.module.customer.vo.CustomerSummaryVO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 客户档案、归属、跟进计时与审计的数据访问。 */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
    /** 按权限和筛选条件分页查询。 */
    IPage<CustomerSummaryVO> list(
        Page<CustomerSummaryVO> page,
        @Param("s") DataScope scope,
        @Param("now") LocalDateTime now,
        @Param("q") CustomerQuery query
    );
    /** 查询指定记录的可见摘要。 */
    CustomerSummaryVO summary(
        @Param("id") Long id,
        @Param("s") DataScope scope,
        @Param("now") LocalDateTime now
    );

    /** 锁定未删除记录，供事务内条件更新。 */
    @Select("SELECT * FROM crm_customer WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Customer lock(Long id);

    /** 带公海状态和版本条件原子更新客户归属。 */
    @Update(
        "UPDATE crm_customer SET owner_id=#{owner},team_id=#{team},sea_type='PRIVATE',customer_status='NEW',ownership_started_at=#{now},last_effective_follow_at=NULL,recycle_warning_key=NULL,recycle_warned_at=NULL,recycle_extension_key=NULL,recycle_extension_until=NULL,warning_at=#{warningAt},first_contact_deadline_at=#{deadlineAt},previous_owner_id=NULL,return_priority_until=NULL,claim_quarter=#{quarter},ownership_seq=ownership_seq+1,version=version+1,updated_by=#{actor},updated_at=#{now} WHERE id=#{id} AND version=#{version} AND deleted=0 AND sea_type='PUBLIC' AND owner_id IS NULL"
    )
    int claim(
        @Param("id") Long id,
        @Param("version") long version,
        @Param("owner") Long owner,
        @Param("team") Long team,
        @Param("warningAt") LocalDateTime warningAt,
        @Param("deadlineAt") LocalDateTime deadlineAt,
        @Param("quarter") String quarter,
        @Param("actor") Long actor,
        @Param("now") LocalDateTime now
    );

    /** 带私海状态和版本条件转交客户。 */
    @Update(
        "UPDATE crm_customer SET owner_id=#{owner},team_id=#{team},ownership_seq=ownership_seq+1,version=version+1,updated_by=#{actor},updated_at=#{now} WHERE id=#{id} AND version=#{version} AND deleted=0 AND sea_type='PRIVATE'"
    )
    int transfer(
        @Param("id") Long id,
        @Param("version") long version,
        @Param("owner") Long owner,
        @Param("team") Long team,
        @Param("actor") Long actor,
        @Param("now") LocalDateTime now
    );

    /** 统计负责人当前私海客户持有量。 */
    @Select(
        "SELECT COUNT(*) FROM crm_customer WHERE owner_id=#{owner} AND sea_type='PRIVATE' AND deleted=0"
    )
    int privateHoldCount(Long owner);

    /** 查询未删除的客户记录。 */
    @Select("SELECT * FROM crm_customer WHERE id=#{id} AND deleted=0")
    Customer active(Long id);

    /** 锁定客户查重互斥记录，串行保护重复校验。 */
    @Select(
        "SELECT id FROM sys_config WHERE config_type='PARAM' AND config_group='crm.customer' AND config_key='write_mutex' FOR UPDATE"
    )
    Long lockDuplicateGuard();

    /** 锁定名称或信用代码匹配的查重候选客户。 */
    @Select(
        "SELECT * FROM crm_customer WHERE deleted=0 AND id != #{exceptId} AND (normalized_name=#{name} " +
            "OR (#{credit} IS NOT NULL AND unified_social_credit_code=#{credit})) FOR UPDATE"
    )
    List<Customer> duplicateCandidates(
        @Param("exceptId") long exceptId,
        @Param("name") String name,
        @Param("credit") String credit
    );

    /** 按版本保存修改字段。 */
    @Update(
        "UPDATE crm_customer SET customer_name=#{c.customerName},normalized_name=#{c.normalizedName}," +
            "unified_social_credit_code=#{c.unifiedSocialCreditCode},primary_phone=#{c.primaryPhone},normalized_primary_phone=#{c.normalizedPrimaryPhone}," +
            "source_code=#{c.sourceCode},industry_code=#{c.industryCode},region=#{c.region},company_size=#{c.companySize},remark=#{c.remark}," +
            "customer_level=#{c.customerLevel},tags_json=#{c.tagsJson},updated_by=#{actor},updated_at=#{now},version=version+1 " +
            "WHERE id=#{c.id} AND version=#{c.version} AND deleted=0"
    )
    int save(
        @Param("c") Customer customer,
        @Param("actor") Long actor,
        @Param("now") LocalDateTime now
    );

    /** 更新最近有效跟进时间及首联状态。 */
    @Update(
        "UPDATE crm_customer SET last_effective_follow_at=GREATEST(COALESCE(last_effective_follow_at,#{followedAt}),#{followedAt}),customer_status=CASE WHEN customer_status='NEW' THEN 'FOLLOWING' ELSE customer_status END,version=version+1,updated_by=#{actor},updated_at=#{now} WHERE id=#{id} AND version=#{version} AND deleted=0 AND sea_type='PRIVATE'"
    )
    int applyEffectiveFollow(
        @Param("id") Long id,
        @Param("version") long version,
        @Param("followedAt") LocalDateTime followedAt,
        @Param("actor") Long actor,
        @Param("now") LocalDateTime now
    );

    /** 按剩余有效记录恢复客户跟进计时。 */
    @Update(
        "UPDATE crm_customer SET last_effective_follow_at=#{followedAt},customer_status=CASE WHEN #{followedAt} IS NULL AND customer_status='FOLLOWING' THEN 'NEW' ELSE customer_status END,version=version+1,updated_by=#{actor},updated_at=#{now} WHERE id=#{id} AND version=#{version} AND deleted=0 AND sea_type='PRIVATE'"
    )
    int restoreFollowClock(
        @Param("id") Long id,
        @Param("version") long version,
        @Param("followedAt") LocalDateTime followedAt,
        @Param("actor") Long actor,
        @Param("now") LocalDateTime now
    );

    /** 按版本逻辑删除客户并保留历史。 */
    @Update(
        "UPDATE crm_customer SET deleted=1,deleted_at=#{now},updated_by=#{actor},updated_at=#{now},version=version+1 " +
            "WHERE id=#{id} AND version=#{version} AND deleted=0"
    )
    int softDelete(
        @Param("id") Long id,
        @Param("version") long version,
        @Param("actor") Long actor,
        @Param("now") LocalDateTime now
    );

    /** 统计阻止删除或权限变更的关联业务数量。 */
    @Select(
        "SELECT (SELECT COUNT(*) FROM crm_follow_record WHERE customer_id=#{id})" +
            "+(SELECT COUNT(*) FROM crm_opportunity WHERE customer_id=#{id})" +
            "+(SELECT COUNT(*) FROM crm_contract WHERE customer_id=#{id})" +
            "+(SELECT COUNT(*) FROM crm_reminder WHERE customer_id=#{id})" +
            "+(SELECT COUNT(*) FROM crm_approval WHERE biz_type='CUSTOMER' AND biz_id=#{id})" +
            "+(SELECT COUNT(*) FROM crm_attachment WHERE biz_type='CUSTOMER' AND biz_id=#{id})" +
            "+(SELECT COUNT(*) FROM crm_todo WHERE biz_type='CUSTOMER' AND biz_id=#{id})" +
            "+(SELECT COUNT(*) FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=#{id} " +
            "AND action_code NOT IN ('CUSTOMER_CREATED','CUSTOMER_UPDATED','CUSTOMER_CLASSIFIED','CONTACT_CREATED','CONTACT_UPDATED','CONTACT_DELETED'))"
    )
    long businessLinks(Long id);

    /** 写入本次业务变更的审计快照。 */
    @Insert(
        "INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,operator_team_id,occurred_at,ownership_seq,before_json,after_json,reason) " +
            "VALUES('CUSTOMER',#{id},#{action},#{actor},#{team},#{now},#{seq},#{before},#{after},#{reason})"
    )
    int audit(
        @Param("id") Long id,
        @Param("action") String action,
        @Param("actor") Long actor,
        @Param("team") Long team,
        @Param("now") LocalDateTime now,
        @Param("seq") Long seq,
        @Param("before") String before,
        @Param("after") String after,
        @Param("reason") String reason
    );

    /** 写入客户归属变化及历史只读权限记录。 */
    @Insert(
        "INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,operator_team_id,occurred_at,ownership_seq,old_owner_id,new_owner_id,new_team_id,handover_readonly_until,before_json,after_json,reason) VALUES('CUSTOMER',#{id},#{action},#{actor},#{operatorTeam},#{now},#{seq},#{oldOwner},#{newOwner},#{newTeam},#{readonlyUntil},#{before},#{after},#{reason})"
    )
    int ownershipAudit(
        @Param("id") Long id,
        @Param("action") String action,
        @Param("actor") Long actor,
        @Param("operatorTeam") Long operatorTeam,
        @Param("now") LocalDateTime now,
        @Param("seq") Long seq,
        @Param("oldOwner") Long oldOwner,
        @Param("newOwner") Long newOwner,
        @Param("newTeam") Long newTeam,
        @Param("readonlyUntil") LocalDateTime readonlyUntil,
        @Param("before") String before,
        @Param("after") String after,
        @Param("reason") String reason
    );
}
