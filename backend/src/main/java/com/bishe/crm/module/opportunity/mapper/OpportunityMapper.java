package com.bishe.crm.module.opportunity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bishe.crm.module.auth.security.DataScope;
import com.bishe.crm.module.opportunity.dto.OpportunityQuery;
import com.bishe.crm.module.opportunity.entity.Opportunity;
import com.bishe.crm.module.opportunity.entity.OpportunityCollaborator;
import com.bishe.crm.module.opportunity.entity.OpportunityStageLog;
import com.bishe.crm.module.opportunity.vo.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OpportunityMapper extends BaseMapper<Opportunity> {
    String SCOPE = "<choose><when test=\"s.role == 'ADMIN'\">1=1</when>"
            + "<when test=\"s.role == 'MANAGER'\">o.team_id=#{s.teamId}</when>"
            + "<otherwise>(o.owner_id=#{s.userId} OR EXISTS (SELECT 1 FROM crm_opportunity_collaborator cx WHERE cx.opportunity_id=o.id AND cx.user_id=#{s.userId} AND cx.collaborator_status='ACTIVE'))</otherwise></choose>";
    String FIELDS = "SELECT o.id,o.customer_id,c.customer_name,o.opportunity_name,o.owner_id,u.real_name owner_name,o.team_id,t.team_name,"
            + "o.stage_code,o.activity_status,o.stagnation_days,o.grace_days,o.expected_amount,o.expected_close_date,"
            + "o.last_effective_progress_at,o.stage_changed_at,o.close_pending_at,o.version,o.created_at,o.updated_at "
            + "FROM crm_opportunity o JOIN crm_customer c ON c.id=o.customer_id JOIN sys_user u ON u.id=o.owner_id JOIN sys_team t ON t.id=o.team_id ";

    @Select("<script>" + FIELDS + "WHERE " + SCOPE
            + "<if test=\"q.keyword != null and q.keyword != ''\"> AND (o.opportunity_name LIKE CONCAT('%',#{q.keyword},'%') OR c.customer_name LIKE CONCAT('%',#{q.keyword},'%'))</if>"
            + "<if test=\"q.stageCode != null\"> AND o.stage_code=#{q.stageCode}</if>"
            + "<if test=\"q.activityStatus != null\"> AND o.activity_status=#{q.activityStatus}</if>"
            + "<if test=\"q.ownerId != null\"> AND o.owner_id=#{q.ownerId}</if> ORDER BY o.id DESC</script>")
    IPage<OpportunitySummaryVO> list(Page<OpportunitySummaryVO> page, @Param("s") DataScope scope, @Param("q") OpportunityQuery query);

    @Select("<script>" + FIELDS + "WHERE o.id=#{id} AND " + SCOPE + "</script>")
    OpportunitySummaryVO summary(@Param("id") Long id, @Param("s") DataScope scope);

    @Select("SELECT * FROM crm_opportunity WHERE id=#{id} FOR UPDATE")
    Opportunity lock(Long id);

    @Insert("INSERT INTO crm_opportunity(customer_id,opportunity_name,owner_id,team_id,stage_code,activity_status,stagnation_days,grace_days,threshold_customized,expected_amount,expected_close_date,last_effective_progress_at,stage_changed_at,created_by,created_at,updated_at) "
            + "VALUES(#{o.customerId},#{o.opportunityName},#{o.ownerId},#{o.teamId},#{o.stageCode},'ACTIVE',#{o.stagnationDays},#{o.graceDays},0,#{o.expectedAmount},#{o.expectedCloseDate},#{o.lastEffectiveProgressAt},#{o.stageChangedAt},#{o.createdBy},#{o.createdAt},#{o.updatedAt})")
    @Options(useGeneratedKeys=true,keyProperty="o.id") int insertOpportunity(@Param("o") Opportunity opportunity);

    @Update("UPDATE crm_opportunity SET opportunity_name=#{o.opportunityName},expected_amount=#{o.expectedAmount},expected_close_date=#{o.expectedCloseDate},version=version+1,updated_at=#{now} WHERE id=#{o.id} AND version=#{o.version}")
    int updateBasic(@Param("o") Opportunity opportunity, @Param("now") LocalDateTime now);

    @Update("UPDATE crm_opportunity SET stage_code=#{toStage},activity_status=#{activity},stage_changed_at=#{now},last_effective_progress_at=#{progressAt},close_pending_at=#{closePendingAt},terminal_at=#{terminalAt},close_reason=#{closeReason},version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}")
    int updateStage(@Param("id") Long id,@Param("version") Long version,@Param("toStage") String toStage,@Param("activity") String activity,@Param("closePendingAt") LocalDateTime closePendingAt,@Param("terminalAt") LocalDateTime terminalAt,@Param("closeReason") String closeReason,@Param("progressAt") LocalDateTime progressAt,@Param("now") LocalDateTime now);

    @Select("SELECT * FROM crm_opportunity WHERE id=#{id}")
    Opportunity find(Long id);

    @Update("UPDATE crm_opportunity SET quote_amount=#{amount},quote_at=#{now},version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}")
    int quote(@Param("id") Long id,@Param("version") Long version,@Param("amount") BigDecimal amount,@Param("now") LocalDateTime now);

    @Update("UPDATE crm_opportunity SET customer_confirm_result=#{description},customer_confirm_at=#{now},version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}")
    int confirmation(@Param("id") Long id,@Param("version") Long version,@Param("description") String description,@Param("now") LocalDateTime now);

    @Update("UPDATE crm_opportunity SET version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}")
    int bumpVersion(@Param("id") Long id,@Param("version") Long version,@Param("now") LocalDateTime now);

    @Update("UPDATE crm_opportunity SET signed_confirmed_at=#{now},version=version+1,updated_at=#{now} WHERE id=#{id} AND stage_code='DEAL' AND version=#{version}")
    int markSignedConfirmed(@Param("id") Long id,@Param("version") Long version,@Param("now") LocalDateTime now);

    @Update("UPDATE crm_opportunity SET activity_status='ACTIVE',last_effective_progress_at=#{now},close_pending_at=NULL,version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version} AND activity_status='CLOSE_PENDING'")
    int resume(@Param("id") Long id,@Param("version") Long version,@Param("now") LocalDateTime now);

    @Update("UPDATE crm_opportunity_collaborator SET collaborator_status='LEFT',left_at=#{now},version=version+1,updated_at=#{now} WHERE opportunity_id=#{id} AND collaborator_status='ACTIVE'")
    int endCollaborators(@Param("id") Long id,@Param("now") LocalDateTime now);

    @Select("SELECT id,real_name realName,username,team_id teamId FROM sys_user WHERE team_id=#{teamId} AND role_code='SPECIALIST' AND account_status='ACTIVE' ORDER BY id")
    List<java.util.Map<String,Object>> members(Long teamId);

    @Select("<script>SELECT DISTINCT u.id,u.real_name realName FROM crm_opportunity o JOIN sys_user u ON u.id=o.owner_id WHERE " + SCOPE + " ORDER BY u.id</script>")
    List<java.util.Map<String,Object>> owners(@Param("s") DataScope scope);

    @Select("SELECT id,contact_name contactName,phone FROM crm_contact WHERE customer_id=#{customerId} ORDER BY is_primary DESC,id")
    List<java.util.Map<String,Object>> minimalContacts(Long customerId);

    @Select("SELECT id,action_code actionCode,operator_id operatorId,occurred_at occurredAt,before_json beforeJson,after_json afterJson,reason FROM crm_operation_log WHERE biz_type='OPPORTUNITY' AND biz_id=#{id} ORDER BY id DESC")
    List<java.util.Map<String,Object>> operationHistory(Long id);

    @Update("UPDATE crm_customer c SET customer_status=CASE WHEN EXISTS(SELECT 1 FROM crm_opportunity o WHERE o.customer_id=c.id AND o.stage_code NOT IN ('LOST','CLOSED')) OR EXISTS(SELECT 1 FROM crm_contract k WHERE k.customer_id=c.id AND (k.outstanding_amount>0 OR k.contract_status IN ('SIGNED','PERFORMING'))) THEN 'PROTECTED' WHEN c.last_effective_follow_at IS NOT NULL THEN 'FOLLOWING' ELSE 'NEW' END,version=version+1,updated_at=#{now} WHERE c.id=#{customerId} AND c.deleted=0 AND c.sea_type='PRIVATE' AND c.customer_status NOT IN ('DEAL','DEAL_PENDING')")
    int refreshCustomerProtection(@Param("customerId") Long customerId,@Param("now") LocalDateTime now);

    @Select("<script>SELECT COUNT(*) FROM crm_customer c WHERE c.id=#{customerId} AND c.deleted=0 AND c.sea_type='PRIVATE' AND <choose><when test=\"s.role == 'ADMIN'\">1=1</when><when test=\"s.role == 'MANAGER'\">c.team_id=#{s.teamId}</when><otherwise>c.owner_id=#{s.userId}</otherwise></choose></script>")
    long writableCustomer(@Param("customerId") Long customerId, @Param("s") DataScope scope);

    @Update("UPDATE crm_opportunity SET activity_status=#{status},close_pending_at=#{closePendingAt},version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}")
    int updateActivity(@Param("id") Long id,@Param("version") Long version,@Param("status") String status,@Param("closePendingAt") LocalDateTime closePendingAt,@Param("now") LocalDateTime now);

    @Update("UPDATE crm_opportunity SET last_effective_progress_at=GREATEST(COALESCE(last_effective_progress_at,#{at}),#{at}),activity_status='ACTIVE',close_pending_at=NULL,version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version} AND stage_code IN ('NEW','FOLLOWING','QUOTED','DEAL')")
    int touchProgress(@Param("id") Long id,@Param("version") Long version,@Param("at") LocalDateTime at,@Param("now") LocalDateTime now);

    @Update("UPDATE crm_opportunity o SET last_effective_progress_at=GREATEST(COALESCE((SELECT MAX(l.occurred_at) FROM crm_opportunity_stage_log l WHERE l.opportunity_id=o.id AND l.action_code<>'AUTO_FOLLOW'),o.created_at),COALESCE(#{at},o.created_at)),version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}")
    int restoreProgress(@Param("id") Long id,@Param("version") Long version,@Param("at") LocalDateTime at,@Param("now") LocalDateTime now);

    @Update("UPDATE crm_opportunity SET stagnation_days=#{days},grace_days=#{grace},threshold_customized=1,version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}")
    int updateThreshold(@Param("id") Long id,@Param("version") Long version,@Param("days") Integer days,@Param("grace") Integer grace,@Param("now") LocalDateTime now);

    @Update("UPDATE crm_opportunity SET stagnation_days=#{days},grace_days=#{grace},version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version} AND threshold_customized=0")
    int updateDefaultThreshold(@Param("id") Long id,@Param("version") Long version,@Param("days") Integer days,@Param("grace") Integer grace,@Param("now") LocalDateTime now);

    @Insert("INSERT INTO crm_opportunity_stage_log(opportunity_id,from_stage,to_stage,action_code,reason,operator_id,occurred_at) VALUES(#{l.opportunityId},#{l.fromStage},#{l.toStage},#{l.actionCode},#{l.reason},#{l.operatorId},#{l.occurredAt})")
    @Options(useGeneratedKeys=true,keyProperty="l.id") int insertStageLog(@Param("l") OpportunityStageLog log);

    @Select("SELECT l.id,l.from_stage,l.to_stage,l.action_code,l.reason,l.operator_id,COALESCE(u.real_name,'系统自动') operator_name,l.occurred_at FROM crm_opportunity_stage_log l LEFT JOIN sys_user u ON u.id=l.operator_id WHERE l.opportunity_id=#{id} ORDER BY l.occurred_at DESC,l.id DESC")
    List<OpportunityStageLogVO> stageHistory(Long id);

    @Select("SELECT oc.id,oc.user_id,u.real_name,u.role_code,u.team_id,oc.split_ratio,oc.collaborator_role,oc.collaborator_status,oc.joined_at,oc.left_at,oc.version FROM crm_opportunity_collaborator oc JOIN sys_user u ON u.id=oc.user_id WHERE oc.opportunity_id=#{id} AND oc.collaborator_status='ACTIVE' ORDER BY oc.id")
    List<OpportunityCollaboratorVO> collaborators(Long id);

    @Select("SELECT * FROM crm_opportunity_collaborator WHERE id=#{id} AND opportunity_id=#{opportunityId} AND collaborator_status='ACTIVE' FOR UPDATE")
    OpportunityCollaborator lockCollaborator(@Param("id") Long id,@Param("opportunityId") Long opportunityId);

    @Select("SELECT COUNT(*) FROM crm_opportunity_collaborator WHERE opportunity_id=#{id} AND collaborator_status='ACTIVE'")
    long activeCollaboratorCount(Long id);

    @Select("SELECT COALESCE(SUM(split_ratio),0) FROM crm_opportunity_collaborator WHERE opportunity_id=#{id} AND collaborator_status='ACTIVE'")
    BigDecimal collaboratorRatio(Long id);

    @Select("SELECT COUNT(*) FROM crm_opportunity_collaborator WHERE opportunity_id=#{opportunityId} AND user_id=#{userId} AND collaborator_status='ACTIVE'")
    long activeCollaborator(@Param("opportunityId") Long opportunityId,@Param("userId") Long userId);

    @Insert("INSERT INTO crm_opportunity_collaborator(opportunity_id,user_id,collaborator_role,split_ratio,joined_at,collaborator_status,created_at,updated_at) VALUES(#{c.opportunityId},#{c.userId},'MEMBER',#{c.splitRatio},#{c.joinedAt},'ACTIVE',#{c.createdAt},#{c.updatedAt})")
    @Options(useGeneratedKeys=true,keyProperty="c.id") int insertCollaborator(@Param("c") OpportunityCollaborator collaborator);

    @Update("UPDATE crm_opportunity_collaborator SET collaborator_status='LEFT',left_at=#{now},version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version} AND collaborator_status='ACTIVE'")
    int removeCollaborator(@Param("id") Long id,@Param("version") Long version,@Param("now") LocalDateTime now);

    @Insert("INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,operator_team_id,occurred_at,before_json,after_json,reason) VALUES('OPPORTUNITY',#{id},#{action},#{actor},#{team},#{now},#{before},#{after},#{reason})")
    int audit(@Param("id") Long id,@Param("action") String action,@Param("actor") Long actor,@Param("team") Long team,@Param("now") LocalDateTime now,@Param("before") String before,@Param("after") String after,@Param("reason") String reason);

    @Select("<script>SELECT o.stage_code,COUNT(*) opportunity_count,COALESCE(SUM(o.expected_amount),0) expected_amount FROM crm_opportunity o WHERE " + SCOPE
            + "<if test=\"from != null\"> AND o.created_at &gt;= #{from}</if><if test=\"to != null\"> AND o.created_at &lt; #{to}</if>"
            + "<if test=\"ownerId != null\"> AND o.owner_id=#{ownerId}</if> GROUP BY o.stage_code ORDER BY FIELD(o.stage_code,'NEW','FOLLOWING','QUOTED','DEAL','WON','LOST','CLOSED')</script>")
    List<OpportunityFunnelVO> funnel(@Param("s") DataScope scope,@Param("from") LocalDateTime from,@Param("to") LocalDateTime to,@Param("ownerId") Long ownerId);

    @Select("SELECT COUNT(*) FROM crm_contract WHERE opportunity_id=#{opportunityId} AND contract_status IN ('SIGNED','PERFORMING','SETTLED')")
    long activeContracts(Long opportunityId);

    @Select("SELECT COUNT(*) FROM crm_attachment a JOIN crm_contract c ON a.biz_type='CONTRACT' AND a.biz_id=c.id WHERE c.opportunity_id=#{id} AND c.contract_status IN ('SIGNED','PERFORMING','SETTLED') AND c.signing_progress='BOTH_SIGNED' AND a.material_type='SIGNED_CONTRACT' AND a.confirm_status='PASSED'")
    long verifiedSignedMaterials(Long id);

    @Select("SELECT COUNT(*) FROM crm_contract WHERE opportunity_id=#{id} AND contract_status<>'VOID' AND NULLIF(TRIM(contract_no),'') IS NOT NULL AND NULLIF(TRIM(our_party_name),'') IS NOT NULL AND NULLIF(TRIM(counterparty_name),'') IS NOT NULL AND contract_amount IS NOT NULL AND signed_at IS NOT NULL")
    long completeContractDetails(Long id);

    @Select("SELECT COUNT(*) FROM crm_payment_record pr JOIN crm_contract c ON c.id=pr.contract_id WHERE c.opportunity_id=#{opportunityId} AND pr.received_amount > 0")
    long receivedPayments(Long opportunityId);

    @Select("SELECT id FROM crm_opportunity WHERE stage_code IN ('NEW','FOLLOWING','QUOTED','DEAL') ORDER BY id")
    List<Long> activityCandidates();

    @Select("SELECT id FROM sys_user WHERE account_status='ACTIVE' AND (role_code='ADMIN' OR (role_code='MANAGER' AND team_id=#{teamId})) ORDER BY id")
    List<Long> reviewers(Long teamId);

    @Insert("INSERT INTO crm_todo(todo_type,biz_type,biz_id,occurrence_key,remind_date,recipient_id,title,source_run_id) VALUES(#{type},'OPPORTUNITY',#{id},#{occurrence},#{now},#{recipient},#{title},#{runId}) ON DUPLICATE KEY UPDATE id=id")
    int todo(@Param("type") String type,@Param("id") Long id,@Param("occurrence") String occurrence,@Param("now") java.time.LocalDate now,@Param("recipient") Long recipient,@Param("title") String title,@Param("runId") String runId);

    @Update("UPDATE crm_todo SET todo_status='RESOLVED',resolved_at=#{now},version=version+1 WHERE biz_type='OPPORTUNITY' AND biz_id=#{id} AND todo_status='PENDING' AND todo_type IN ('OPP_STAGNANT','OPP_CLOSE_PENDING','OPP_ESCALATED')")
    int resolveTodos(@Param("id") Long id,@Param("now") LocalDateTime now);

    @Insert("INSERT INTO crm_job_log(job_name,business_date,logical_run_id,attempt_no,started_at,triggered_by) SELECT 'opportunity-activity-scan',#{date},#{runId},COALESCE(MAX(attempt_no),0)+1,#{now},#{actor} FROM crm_job_log WHERE logical_run_id=#{runId}")
    int startRun(@Param("date") LocalDate date,@Param("runId") String runId,@Param("now") LocalDateTime now,@Param("actor") Long actor);

    @Update("UPDATE crm_job_log SET finished_at=#{now},run_status=#{status},success_count=#{success},failure_count=#{failure},failed_biz_ids_json=#{failed} WHERE logical_run_id=#{runId} AND attempt_no=(SELECT n FROM (SELECT MAX(attempt_no) n FROM crm_job_log WHERE logical_run_id=#{runId}) latest)")
    int finishRun(@Param("runId") String runId,@Param("now") LocalDateTime now,@Param("status") String status,@Param("success") int success,@Param("failure") int failure,@Param("failed") String failed);
}
