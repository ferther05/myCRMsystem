/* 文件作用：访问回款计划、到账记录、审批、待办和合同汇总数据。 */
package com.bishe.crm.module.contract.mapper;

import com.bishe.crm.module.auth.security.DataScope;
import com.bishe.crm.module.contract.entity.Contract;
import com.bishe.crm.module.contract.entity.PaymentPeriod;
import com.bishe.crm.module.contract.entity.PaymentPlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 合同回款数据访问。 */
@Mapper
public interface PaymentMapper {
    String CONTRACT_SCOPE =
        "<choose><when test=\"s.role == 'ADMIN'\">1=1</when><otherwise>EXISTS(SELECT 1 FROM crm_customer scope_customer WHERE scope_customer.id=c.customer_id AND scope_customer.deleted=0 AND scope_customer.sea_type='PRIVATE' AND <choose><when test=\"s.role == 'MANAGER'\">scope_customer.team_id=#{s.teamId}</when><otherwise>scope_customer.owner_id=#{s.userId}</otherwise></choose>)</otherwise></choose>";

    /** 按数据范围查询合同。 */
    @Select("<script>SELECT c.* FROM crm_contract c WHERE c.id=#{id} AND " + CONTRACT_SCOPE + "</script>")
    Contract scopedContract(@Param("id") Long id, @Param("s") DataScope scope);

    /** 锁定合同，串行化同合同的计划换版和到账核销。 */
    @Select("<script>SELECT c.* FROM crm_contract c WHERE c.id=#{id} AND " + CONTRACT_SCOPE + " FOR UPDATE</script>")
    Contract lockContract(@Param("id") Long id, @Param("s") DataScope scope);

    /** 查询合同的全部计划版本和审核人。 */
    @Select("SELECT p.*,su.real_name submitter_name,au.real_name approver_name FROM crm_payment_plan p LEFT JOIN sys_user su ON su.id=p.submitted_by LEFT JOIN sys_user au ON au.id=p.approved_by WHERE p.contract_id=#{id} ORDER BY p.version_no DESC")
    List<Map<String, Object>> plans(Long id);

    /** 查询计划期次。 */
    @Select("SELECT * FROM crm_payment_period WHERE plan_id=#{id} ORDER BY period_no")
    List<PaymentPeriod> periods(Long id);

    /** 查询全部到账，作废记录仍保留展示。 */
    @Select("SELECT r.*,u.real_name registered_by_name,vu.real_name voided_by_name FROM crm_payment_record r JOIN sys_user u ON u.id=r.registered_by LEFT JOIN sys_user vu ON vu.id=r.voided_by WHERE r.contract_id=#{id} ORDER BY r.received_at DESC,r.id DESC")
    List<Map<String, Object>> receipts(Long id);

    /** 查询当前生效计划。 */
    @Select("SELECT * FROM crm_payment_plan WHERE contract_id=#{id} AND is_current=1 LIMIT 1")
    PaymentPlan currentPlan(Long id);

    /** 在合同锁内取得下一个计划版本号。 */
    @Select("SELECT COALESCE(MAX(version_no),0)+1 FROM crm_payment_plan WHERE contract_id=#{id}")
    int nextPlanVersion(Long id);

    /** 保存计划草稿。 */
    @Insert("INSERT INTO crm_payment_plan(contract_id,version_no,total_planned_amount,change_description,approval_status,is_current,version,created_by,created_at,updated_at) VALUES(#{p.contractId},#{p.versionNo},#{p.totalPlannedAmount},#{p.changeDescription},'DRAFT',0,0,#{actor},#{now},#{now})")
    @Options(useGeneratedKeys = true, keyProperty = "p.id")
    int insertPlan(@Param("p") PaymentPlan plan, @Param("actor") Long actor, @Param("now") LocalDateTime now);

    /** 新增计划期次。 */
    @Insert("INSERT INTO crm_payment_period(plan_id,period_no,planned_amount,due_date,received_amount,period_status,version,trigger_node,created_at,updated_at) VALUES(#{p.planId},#{p.periodNo},#{p.plannedAmount},#{p.dueDate},0,'UNPAID',0,#{p.triggerNode},#{now},#{now})")
    int insertPeriod(@Param("p") PaymentPeriod period, @Param("now") LocalDateTime now);

    /** 锁定待编辑计划。 */
    @Select("SELECT * FROM crm_payment_plan WHERE id=#{id} AND contract_id=#{contractId} FOR UPDATE")
    PaymentPlan lockPlan(@Param("id") Long id, @Param("contractId") Long contractId);

    /** 软撤回旧草稿期次并重新写入新明细，草稿尚未对外生效。 */
    @Delete("DELETE FROM crm_payment_period WHERE plan_id=#{id} AND received_amount=0")
    int deleteDraftPeriods(Long id);

    /** 更新计划草稿金额和说明。 */
    @Update("UPDATE crm_payment_plan SET total_planned_amount=#{amount},change_description=#{description},version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version} AND approval_status IN ('DRAFT','RETURNED') AND is_current=0")
    int updateDraftPlan(@Param("id") Long id, @Param("amount") BigDecimal amount, @Param("description") String description, @Param("version") Long version, @Param("now") LocalDateTime now);

    /** 创建计划确认审批单。 */
    @Insert("INSERT INTO crm_approval(approval_type,biz_type,biz_id,applicant_id,approval_status,reason,submitted_at,extra_json,version,created_at,updated_at) VALUES(#{type},'CONTRACT',#{contractId},#{actor},'PENDING',#{reason},#{now},JSON_OBJECT('planId',#{planId}),0,#{now},#{now})")
    int insertApproval(@Param("type") String type, @Param("contractId") Long contractId, @Param("planId") Long planId, @Param("actor") Long actor, @Param("reason") String reason, @Param("now") LocalDateTime now, @Param("approvalId") Long approvalId);

    /** 读取当前事务中刚创建的审批主键。 */
    @Select("SELECT LAST_INSERT_ID()")
    Long lastInsertId();

    /** 绑定审批并提交计划。 */
    @Update("UPDATE crm_payment_plan SET approval_status='PENDING',approval_id=#{approvalId},submitted_by=#{actor},submitted_at=#{now},version=version+1,updated_at=#{now} WHERE id=#{planId} AND contract_id=#{contractId} AND version=#{version} AND approval_status IN ('DRAFT','RETURNED')")
    int submitPlan(@Param("planId") Long planId, @Param("contractId") Long contractId, @Param("approvalId") Long approvalId, @Param("actor") Long actor, @Param("version") Long version, @Param("now") LocalDateTime now);

    /** 锁定审批单并校验其业务归属。 */
    @Select("SELECT * FROM crm_approval WHERE id=#{id} AND biz_type='CONTRACT' AND approval_type=#{type} FOR UPDATE")
    Map<String, Object> lockApproval(@Param("id") Long id, @Param("type") String type);

    /** 写入审批结果。 */
    @Update("UPDATE crm_approval SET approval_status=#{status},approver_id=#{actor},reason=#{reason},processed_at=#{now},version=version+1,updated_at=#{now} WHERE id=#{id} AND approval_status='PENDING' AND version=#{version}")
    int reviewApproval(@Param("id") Long id, @Param("actor") Long actor, @Param("status") String status, @Param("reason") String reason, @Param("version") Long version, @Param("now") LocalDateTime now);

    /** 设置计划审核结果；通过后切换当前计划由服务端在事务中完成。 */
    @Update("UPDATE crm_payment_plan SET approval_status=#{status},approved_by=#{actor},approved_at=#{now},version=version+1,updated_at=#{now} WHERE id=#{id} AND contract_id=#{contractId} AND approval_status='PENDING' AND version=#{version}")
    int reviewPlan(@Param("id") Long id, @Param("contractId") Long contractId, @Param("actor") Long actor, @Param("status") String status, @Param("version") Long version, @Param("now") LocalDateTime now);

    /** 使旧计划失效但保留所有旧期次。 */
    @Update("UPDATE crm_payment_plan SET approval_status='CHANGED',is_current=0,version=version+1,updated_at=#{now} WHERE contract_id=#{contractId} AND is_current=1")
    int supersedeCurrentPlan(@Param("contractId") Long contractId, @Param("now") LocalDateTime now);

    /** 新版承接旧版剩余应收，旧期次金额和到账记录保持不变。 */
    @Update("UPDATE crm_payment_period pp JOIN crm_payment_plan p ON p.id=pp.plan_id SET pp.replaced_by_plan_id=#{newPlanId},pp.version=pp.version+1,pp.updated_at=#{now} WHERE p.contract_id=#{contractId} AND p.is_current=1 AND pp.received_amount<pp.planned_amount")
    int replaceUnpaidPeriods(@Param("contractId") Long contractId, @Param("newPlanId") Long newPlanId, @Param("now") LocalDateTime now);

    /** 历史到账作废后暂停当前计划，按新余额换版审批后才能继续收款。 */
    @Update("UPDATE crm_payment_plan SET approval_status='SUSPENDED',version=version+1,updated_at=#{now} WHERE contract_id=#{id} AND is_current=1")
    int suspendCurrentPlan(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 核对到账凭证确实属于当前合同。 */
    @Select("SELECT COUNT(*) FROM crm_attachment WHERE id=#{attachmentId} AND biz_type='CONTRACT' AND biz_id=#{contractId}")
    int ownsAttachment(@Param("attachmentId") Long attachmentId, @Param("contractId") Long contractId);

    /** 核对幂等键是否已经入账。 */
    @Select("SELECT COUNT(*) FROM crm_payment_record WHERE idempotency_key=#{key}")
    int receiptKeyExists(String key);

    /** 将审核通过的计划设为当前版本。 */
    @Update("UPDATE crm_payment_plan SET approval_status='ACTIVE',is_current=1,effective_at=#{now},version=version+1,updated_at=#{now} WHERE id=#{id} AND contract_id=#{contractId} AND approval_status='APPROVED' AND is_current=0")
    int activatePlan(@Param("id") Long id, @Param("contractId") Long contractId, @Param("now") LocalDateTime now);

    /** 锁定期次及所属计划。 */
    @Select("SELECT pp.*,p.contract_id,p.approval_status plan_status,p.is_current FROM crm_payment_period pp JOIN crm_payment_plan p ON p.id=pp.plan_id WHERE pp.id=#{id} AND p.contract_id=#{contractId} FOR UPDATE")
    Map<String, Object> lockPeriod(@Param("id") Long id, @Param("contractId") Long contractId);

    /** 查询期次所有已登记到账（不含作废）。 */
    @Select("SELECT COALESCE(SUM(received_amount),0) FROM crm_payment_record WHERE period_id=#{id} AND voided_at IS NULL")
    BigDecimal periodReceived(Long id);

    /** 新增到账记录。 */
    @Insert("INSERT INTO crm_payment_record(contract_id,period_id,received_amount,received_at,payment_method,voucher_attachment_id,registered_by,remark,difference_description,idempotency_key,version,created_at,performance_owner_id,performance_team_id,performance_legacy) SELECT #{contractId},#{periodId},#{amount},#{receivedAt},#{method},#{voucher},#{actor},#{remark},#{difference},#{key},0,#{now},owner_id,team_id,0 FROM crm_contract WHERE id=#{contractId}")
    int insertReceipt(@Param("contractId") Long contractId, @Param("periodId") Long periodId, @Param("amount") BigDecimal amount, @Param("receivedAt") LocalDateTime receivedAt, @Param("method") String method, @Param("voucher") Long voucher, @Param("actor") Long actor, @Param("remark") String remark, @Param("difference") String difference, @Param("key") String key, @Param("now") LocalDateTime now);

    /** 更新期次已收汇总及当前状态。 */
    @Update("UPDATE crm_payment_period SET received_amount=#{received},period_status=#{status},version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}")
    int updatePeriod(@Param("id") Long id, @Param("received") BigDecimal received, @Param("status") String status, @Param("version") Long version, @Param("now") LocalDateTime now);

    /** 锁定到账记录，防止重复作废。 */
    @Select("SELECT * FROM crm_payment_record WHERE id=#{id} AND contract_id=#{contractId} FOR UPDATE")
    Map<String, Object> lockReceipt(@Param("id") Long id, @Param("contractId") Long contractId);

    /** 将误录记为作废，保留原始金额及登记信息。 */
    @Update("UPDATE crm_payment_record SET voided_at=#{now},voided_by=#{actor},void_reason=#{reason},version=version+1 WHERE id=#{id} AND contract_id=#{contractId} AND voided_at IS NULL AND version=#{version}")
    int voidReceipt(@Param("id") Long id, @Param("contractId") Long contractId, @Param("actor") Long actor, @Param("now") LocalDateTime now, @Param("reason") String reason, @Param("version") Long version);

    /** 重算合同有效到账总额。 */
    @Select("SELECT COALESCE(SUM(received_amount),0) FROM crm_payment_record WHERE contract_id=#{id} AND voided_at IS NULL")
    BigDecimal totalReceived(Long id);

    /** 判断是否存在已逾期的当前有效期次。 */
    @Select("SELECT COUNT(*) FROM crm_payment_period pp JOIN crm_payment_plan p ON p.id=pp.plan_id WHERE p.contract_id=#{id} AND p.is_current=1 AND p.approval_status='ACTIVE' AND pp.due_date<#{date} AND pp.received_amount<pp.planned_amount")
    int overdueCount(@Param("id") Long id, @Param("date") LocalDate date);

    /** 更新合同回款汇总；传入状态由服务端依据收款、逾期和签约事实计算。 */
    @Update("UPDATE crm_contract SET received_amount=#{received},outstanding_amount=#{outstanding},payment_status=#{status},contract_status=CASE WHEN #{status}='SETTLED' THEN 'SETTLED' WHEN contract_status='SETTLED' THEN 'PERFORMING' ELSE contract_status END,current_plan_id=COALESCE(#{planId},current_plan_id),version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}")
    int updateContractPayment(@Param("id") Long id, @Param("received") BigDecimal received, @Param("outstanding") BigDecimal outstanding, @Param("status") String status, @Param("planId") Long planId, @Param("version") Long version, @Param("now") LocalDateTime now);

    /** 读取合同客户 id。 */
    @Select("SELECT customer_id FROM crm_contract WHERE id=#{id}")
    Long customerId(Long id);

    /** 最终成交要求客户所有有效合同已结清或终止。 */
    @Update("UPDATE crm_customer c SET customer_status='DEAL',permanent_deal_at=COALESCE(permanent_deal_at,#{now}),version=version+1,updated_at=#{now} WHERE c.id=#{customerId} AND c.deleted=0 AND c.sea_type='PRIVATE' AND EXISTS(SELECT 1 FROM crm_contract k WHERE k.customer_id=c.id) AND NOT EXISTS(SELECT 1 FROM crm_contract k WHERE k.customer_id=c.id AND k.contract_status NOT IN ('SETTLED','TERMINATED','VOID')) AND NOT EXISTS(SELECT 1 FROM crm_opportunity o WHERE o.customer_id=c.id AND o.stage_code NOT IN ('WON','LOST','CLOSED'))")
    int finalizeCustomer(@Param("customerId") Long customerId, @Param("now") LocalDateTime now);

    /** 管理员冲正误录后重新保护尚未结清的客户。 */
    @Update("UPDATE crm_customer c SET customer_status='PROTECTED',version=version+1,updated_at=#{now} WHERE c.id=#{customerId} AND c.deleted=0 AND c.sea_type='PRIVATE' AND c.customer_status='DEAL' AND EXISTS(SELECT 1 FROM crm_contract k WHERE k.customer_id=c.id AND k.contract_status NOT IN ('SETTLED','TERMINATED','VOID'))")
    int protectCustomer(@Param("customerId") Long customerId, @Param("now") LocalDateTime now);

    /** 创建无应收终止审批。 */
    @Insert("INSERT INTO crm_approval(approval_type,biz_type,biz_id,applicant_id,approval_status,reason,submitted_at,extra_json,version,created_at,updated_at) VALUES('CONTRACT_TERMINATION','CONTRACT',#{contractId},#{actor},'PENDING',#{reason},#{now},JSON_OBJECT('applicantReason',#{reason},'noReceivableExplanation',#{explanation}),0,#{now},#{now})")
    int insertTerminationApproval(@Param("contractId") Long contractId, @Param("actor") Long actor, @Param("reason") String reason, @Param("explanation") String explanation, @Param("now") LocalDateTime now, @Param("approvalId") Long approvalId);

    /** 终止审批通过后关闭合同并保留应收声明。 */
    @Update("UPDATE crm_contract SET contract_status='TERMINATED',payment_status='TERMINATED',outstanding_amount=0,termination_approval_id=#{approvalId},termination_reason=#{reason},terminated_at=#{now},version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version} AND contract_status NOT IN ('SETTLED','TERMINATED','VOID') AND received_amount=0")
    int terminateContract(@Param("id") Long id, @Param("approvalId") Long approvalId, @Param("reason") String reason, @Param("version") Long version, @Param("now") LocalDateTime now);

    /** 标记终止审批状态并保存审批人意见。 */
    @Update("UPDATE crm_approval SET approval_status=#{status},approver_id=#{actor},reason=#{opinion},processed_at=#{now},version=version+1,updated_at=#{now} WHERE id=#{id} AND approval_type='CONTRACT_TERMINATION' AND biz_type='CONTRACT' AND biz_id=#{contractId} AND approval_status='PENDING' AND version=#{version}")
    int reviewTermination(@Param("id") Long id, @Param("contractId") Long contractId, @Param("actor") Long actor, @Param("status") String status, @Param("opinion") String opinion, @Param("version") Long version, @Param("now") LocalDateTime now);

    /** 查询合同当前无应收终止审批。 */
    @Select("SELECT a.*,app.real_name applicant_name,ap.real_name approver_name,JSON_UNQUOTE(JSON_EXTRACT(a.extra_json,'$.applicantReason')) applicant_reason,JSON_UNQUOTE(JSON_EXTRACT(a.extra_json,'$.noReceivableExplanation')) no_receivable_explanation FROM crm_approval a JOIN sys_user app ON app.id=a.applicant_id LEFT JOIN sys_user ap ON ap.id=a.approver_id WHERE a.biz_type='CONTRACT' AND a.biz_id=#{id} AND a.approval_type='CONTRACT_TERMINATION' ORDER BY a.id DESC LIMIT 1")
    Map<String, Object> termination(Long id);

    /** 查询逾期的当前计划期次及合同负责人。 */
    @Select("SELECT pp.id period_id,p.id plan_id,p.contract_id,c.owner_id,c.team_id FROM crm_payment_period pp JOIN crm_payment_plan p ON p.id=pp.plan_id JOIN crm_contract c ON c.id=p.contract_id WHERE p.is_current=1 AND p.approval_status='ACTIVE' AND pp.due_date<#{date} AND pp.received_amount<pp.planned_amount AND c.contract_status IN ('SIGNED','PERFORMING') ORDER BY pp.due_date,pp.id")
    List<Map<String, Object>> overduePeriods(LocalDate date);

    /** 补跑根据期次主键重新定位合同，不信任旧扫描快照。 */
    @Select("SELECT p.contract_id FROM crm_payment_period pp JOIN crm_payment_plan p ON p.id=pp.plan_id WHERE pp.id=#{id}")
    Long periodContract(Long id);

    /** 查找当前团队经理。 */
    @Select("SELECT id FROM sys_user WHERE team_id=#{teamId} AND role_code='MANAGER' AND account_status='ACTIVE' LIMIT 1")
    Long teamManager(Long teamId);

    /** 更新期次逾期显示状态。 */
    @Update("UPDATE crm_payment_period SET period_status='OVERDUE',version=version+1,updated_at=#{now} WHERE id=#{id} AND received_amount<planned_amount AND period_status IN ('UNPAID','PARTIAL','OVERDUE')")
    int markOverdue(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 终止获批后将计划状态设为终止，原计划和期次仍保留。 */
    @Update("UPDATE crm_payment_plan SET approval_status='TERMINATED',is_current=0,version=version+1,updated_at=#{now} WHERE contract_id=#{id} AND is_current=1")
    int terminatePlan(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 合同终止后取消尚未结束的计划审批，避免留下无法办理的待办。 */
    @Update("UPDATE crm_approval SET approval_status='CANCELLED',processed_at=#{now},version=version+1,updated_at=#{now} WHERE biz_type='CONTRACT' AND biz_id=#{id} AND approval_type='PAYMENT_PLAN' AND approval_status='PENDING'")
    int cancelPendingPlanApprovals(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 同日按事件和接收人去重生成逾期待办。 */
    @Insert("INSERT INTO crm_todo(todo_type,biz_type,biz_id,occurrence_key,remind_date,recipient_id,title,source_run_id) VALUES('PAYMENT_OVERDUE','PAYMENT_PERIOD',#{periodId},#{occurrence},#{date},#{recipient},#{title},#{runId}) ON DUPLICATE KEY UPDATE read_at=IF(crm_todo.todo_status='RESOLVED',NULL,crm_todo.read_at),version=IF(crm_todo.todo_status='RESOLVED',crm_todo.version+1,crm_todo.version),resolved_at=IF(crm_todo.todo_status='RESOLVED',NULL,crm_todo.resolved_at),todo_status=IF(crm_todo.todo_status='RESOLVED','PENDING',crm_todo.todo_status)")
    int insertOverdueTodo(@Param("periodId") Long periodId, @Param("occurrence") String occurrence, @Param("date") LocalDate date, @Param("recipient") Long recipient, @Param("title") String title, @Param("runId") String runId);

    /** 新增或补跑记录化的扫描执行尝试。 */
    @Insert("INSERT INTO crm_job_log(job_name,business_date,logical_run_id,attempt_no,started_at,triggered_by) SELECT 'payment-overdue-scan',#{date},#{runId},COALESCE(MAX(attempt_no),0)+1,#{now},#{actor} FROM crm_job_log WHERE logical_run_id=#{runId}")
    int startOverdueRun(@Param("date") LocalDate date, @Param("runId") String runId, @Param("now") LocalDateTime now, @Param("actor") Long actor);

    /** 记录逾期扫描处理数及失败项。 */
    @Update("UPDATE crm_job_log SET finished_at=#{now},run_status=#{status},success_count=#{success},failure_count=#{failure},failed_biz_ids_json=#{failed},error_summary=#{error} WHERE logical_run_id=#{runId} AND attempt_no=(SELECT n FROM (SELECT MAX(attempt_no) n FROM crm_job_log WHERE logical_run_id=#{runId}) latest)")
    int finishOverdueRun(@Param("runId") String runId, @Param("now") LocalDateTime now, @Param("status") String status, @Param("success") int success, @Param("failure") int failure, @Param("failed") String failed, @Param("error") String error);

    /** 回款到账后解除该期次未完成的逾期待办。 */
    @Update("UPDATE crm_todo SET todo_status='RESOLVED',resolved_at=#{now},version=version+1 WHERE biz_type='PAYMENT_PERIOD' AND biz_id=#{periodId} AND todo_status='PENDING' AND todo_type='PAYMENT_OVERDUE'")
    int resolveOverdueTodos(@Param("periodId") Long periodId, @Param("now") LocalDateTime now);

    /** 为待审批的经理和管理员分别生成待办。 */
    @Insert("INSERT INTO crm_todo(todo_type,biz_type,biz_id,occurrence_key,remind_date,recipient_id,title) SELECT 'APPROVAL','APPROVAL',#{approvalId},CONCAT('approval-',#{approvalId}),#{date},u.id,#{title} FROM sys_user u WHERE u.account_status='ACTIVE' AND (u.role_code='ADMIN' OR (u.role_code='MANAGER' AND u.team_id=#{teamId})) ON DUPLICATE KEY UPDATE id=crm_todo.id")
    int insertApprovalTodos(@Param("approvalId") Long approvalId, @Param("teamId") Long teamId, @Param("date") LocalDate date, @Param("title") String title);

    /** 审批结束后完成当前审批人待办，并解除其他人的副本。 */
    @Update("UPDATE crm_todo SET todo_status=CASE WHEN recipient_id=#{actor} THEN 'DONE' ELSE 'RESOLVED' END,completed_at=CASE WHEN recipient_id=#{actor} THEN #{now} ELSE completed_at END,resolved_at=CASE WHEN #{actor} IS NULL OR recipient_id<>#{actor} THEN #{now} ELSE resolved_at END,version=version+1 WHERE biz_type='APPROVAL' AND biz_id=#{approvalId} AND todo_status='PENDING'")
    int closeApprovalTodos(@Param("approvalId") Long approvalId, @Param("actor") Long actor, @Param("now") LocalDateTime now);

    /** 将关键资金操作与业务变更放在同一数据库事务。 */
    @Insert("INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,operator_team_id,occurred_at,before_json,after_json,reason) VALUES('CONTRACT',#{id},#{action},#{actor},#{team},#{now},#{before},#{after},#{reason})")
    int audit(@Param("id") Long id, @Param("action") String action, @Param("actor") Long actor, @Param("team") Long team, @Param("now") LocalDateTime now, @Param("before") String before, @Param("after") String after, @Param("reason") String reason);
}
