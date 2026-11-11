/* 文件作用：查询本人待办、按业务范围查询审计摘要及管理扫描执行记录。 */
package com.bishe.crm.module.task.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bishe.crm.module.auth.security.DataScope;
import com.bishe.crm.module.task.dto.TaskQuery;
import com.bishe.crm.module.task.entity.JobRun;
import com.bishe.crm.module.task.entity.Todo;
import com.bishe.crm.module.task.vo.TaskViews;
import java.time.LocalDateTime;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 查询范围不能由客户端指定用户或团队。 */
@Mapper
public interface TaskMapper {
    /** 只统计当前接收人，已解除/取消的旧未读记录不进入红点。 */
    @Select("SELECT COALESCE(SUM(CASE WHEN read_at IS NULL THEN 1 ELSE 0 END),0) unread_count, COUNT(*) pending_count FROM crm_todo WHERE recipient_id=#{actor} AND todo_status='PENDING'")
    TaskViews.Summary summary(@Param("actor") Long actor);

    String TODO_WHERE = " WHERE recipient_id=#{actor} " +
        "<if test='q.status != null'>AND todo_status=#{q.status} </if>" +
        "<if test='q.type != null'>AND todo_type=#{q.type} </if>" +
        "<if test='q.unreadOnly == true'>AND read_at IS NULL AND todo_status='PENDING' </if>" +
        "<if test='q.from != null'>AND remind_date &gt;= #{q.from} </if>" +
        "<if test='q.to != null'>AND remind_date &lt;= #{q.to} </if>";

    /** 仅查询当前接收人的待办。 */
    @Select("<script>SELECT * FROM crm_todo" + TODO_WHERE + " ORDER BY remind_date DESC,id DESC</script>")
    IPage<Todo> todos(Page<Todo> page, @Param("actor") Long actor, @Param("q") TaskQuery query);

    /** 点击、标记已读前重新检查待办归属。 */
    @Select("SELECT * FROM crm_todo WHERE id=#{id} AND recipient_id=#{actor}")
    Todo todo(@Param("id") Long id, @Param("actor") Long actor);

    /** 带版本更新已读标记，保持真实业务状态不变。 */
    @Update("UPDATE crm_todo SET read_at=#{now},version=version+1 WHERE id=#{id} AND recipient_id=#{actor} AND version=#{version} AND read_at IS NULL AND todo_status='PENDING'")
    int read(@Param("id") Long id, @Param("actor") Long actor, @Param("version") Long version, @Param("now") LocalDateTime now);

    /** 读取审批跳转所需的最小定位信息。 */
    @Select("SELECT biz_type,biz_id,applicant_id,approval_type FROM crm_approval WHERE id=#{id}")
    Map<String,Object> approval(Long id);

    /** 将回款期次定位到合同，由服务层继续校验合同权限。 */
    @Select("SELECT p.contract_id FROM crm_payment_period pp JOIN crm_payment_plan p ON p.id=pp.plan_id WHERE pp.id=#{id}")
    Long periodContract(Long id);

    String LOG_SCOPE = "<choose><when test=\"s.role == 'ADMIN'\">1=1</when><otherwise>(" +
        "(l.biz_type='CUSTOMER' AND EXISTS(SELECT 1 FROM crm_customer c WHERE c.id=l.biz_id AND c.deleted=0 AND c.sea_type='PRIVATE' AND <choose><when test=\"s.role == 'MANAGER'\">c.team_id=#{s.teamId}</when><otherwise>c.owner_id=#{s.userId}</otherwise></choose>)) OR " +
        "(l.biz_type='OPPORTUNITY' AND EXISTS(SELECT 1 FROM crm_opportunity o JOIN crm_customer c ON c.id=o.customer_id WHERE o.id=l.biz_id AND c.deleted=0 AND c.sea_type='PRIVATE' AND <choose><when test=\"s.role == 'MANAGER'\">c.team_id=#{s.teamId}</when><otherwise>c.owner_id=#{s.userId}</otherwise></choose>)) OR " +
        "(l.biz_type='CONTRACT' AND EXISTS(SELECT 1 FROM crm_contract k JOIN crm_customer c ON c.id=k.customer_id WHERE k.id=l.biz_id AND c.deleted=0 AND c.sea_type='PRIVATE' AND <choose><when test=\"s.role == 'MANAGER'\">c.team_id=#{s.teamId}</when><otherwise>c.owner_id=#{s.userId}</otherwise></choose>)) OR " +
        "(l.biz_type='USER' AND EXISTS(SELECT 1 FROM sys_user u WHERE u.id=l.biz_id AND <choose><when test=\"s.role == 'MANAGER'\">u.team_id=#{s.teamId}</when><otherwise>u.id=#{s.userId}</otherwise></choose>)) OR " +
        "(l.biz_type='TODO' AND EXISTS(SELECT 1 FROM crm_todo t WHERE t.id=l.biz_id AND t.recipient_id=#{s.userId}))" +
        ")</otherwise></choose>";

    /** 只返回权限范围内的操作摘要；原始敏感快照不进入此接口。 */
    @Select("<script>SELECT l.id,l.biz_type,l.biz_id,l.action_code,COALESCE(NULLIF(u.real_name,''),u.username,'系统任务') operator_name,l.occurred_at,l.reason FROM crm_operation_log l LEFT JOIN sys_user u ON u.id=l.operator_id WHERE " + LOG_SCOPE +
        "<if test='q.bizType != null'> AND l.biz_type=#{q.bizType}</if>" +
        "<if test='q.bizId != null'> AND l.biz_id=#{q.bizId}</if>" +
        "<if test='q.action != null and q.action != &quot;&quot;'> AND l.action_code=#{q.action}</if>" +
        "<if test='q.from != null'> AND l.occurred_at &gt;= #{q.from}</if>" +
        "<if test='q.to != null'> AND l.occurred_at &lt; DATE_ADD(#{q.to},INTERVAL 1 DAY)</if> ORDER BY l.id DESC</script>")
    IPage<TaskViews.Operation> operations(Page<TaskViews.Operation> page, @Param("s") DataScope scope, @Param("q") TaskQuery query);

    /** 详情沿用列表范围，禁止按日志ID绕过对象权限。 */
    @Select("<script>SELECT l.id,l.before_json,l.after_json FROM crm_operation_log l WHERE l.id=#{id} AND " + LOG_SCOPE + "</script>")
    Map<String,Object> operationSnapshots(@Param("id") Long id,@Param("s") DataScope scope);

    @Select("SELECT COALESCE(NULLIF(real_name,''),username) FROM sys_user WHERE id=#{id}")
    String userName(Long id);
    @Select("SELECT team_name FROM sys_team WHERE id=#{id}")
    String teamName(Long id);
    /** 日志使用现有字典中文名，停用项仍可解释历史值。 */
    @Select("SELECT display_name FROM sys_config WHERE config_type='DICT' AND config_group=#{group} AND config_key=#{code} LIMIT 1")
    String dictionaryLabel(@Param("group") String group,@Param("code") String code);

    /** 已读与扫描管理操作写入审计，原因由服务端生成。 */
    @Insert("INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,operator_team_id,occurred_at,reason) VALUES(#{biz},#{id},#{action},#{actor},#{team},#{now},#{reason})")
    int audit(@Param("biz") String biz, @Param("id") Long id, @Param("action") String action, @Param("actor") Long actor, @Param("team") Long team, @Param("now") LocalDateTime now, @Param("reason") String reason);

    /** 归属变更前后值与业务更新同事务落库。 */
    @Insert("INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,operator_team_id,occurred_at,reason,before_json,after_json) VALUES(#{biz},#{id},'BUSINESS_HANDED_OVER',#{actor},#{team},#{now},#{reason},#{before},#{after})")
    int handoverAudit(@Param("biz") String biz,@Param("id") Long id,@Param("actor") Long actor,@Param("team") Long team,@Param("now") LocalDateTime now,@Param("reason") String reason,@Param("before") String before,@Param("after") String after);

    /** 管理员执行记录分页。 */
    @Select("<script>SELECT j.*,COALESCE(NULLIF(u.real_name,''),u.username,'系统任务') triggered_by_name,(j.run_status IN ('FAILED','PARTIAL_FAILURE') AND j.failed_biz_ids_json IS NOT NULL AND JSON_LENGTH(j.failed_biz_ids_json)>0 AND NOT EXISTS(SELECT 1 FROM crm_job_log newer WHERE newer.logical_run_id=j.logical_run_id AND newer.attempt_no>j.attempt_no)) retryable FROM crm_job_log j LEFT JOIN sys_user u ON u.id=j.triggered_by WHERE 1=1" +
        "<if test='q.jobName != null'> AND j.job_name=#{q.jobName}</if>" +
        "<if test='q.runStatus != null'> AND j.run_status=#{q.runStatus}</if>" +
        "<if test='q.from != null'> AND j.business_date &gt;= #{q.from}</if>" +
        "<if test='q.to != null'> AND j.business_date &lt;= #{q.to}</if> ORDER BY j.id DESC</script>")
    IPage<JobRun> jobs(Page<JobRun> page, @Param("q") TaskQuery query);

    /** 查询指定执行尝试。 */
    @Select("SELECT * FROM crm_job_log WHERE id=#{id}")
    JobRun job(Long id);

    /** 查询逻辑批次最新一次尝试，拒绝拿旧失败记录重复补跑。 */
    @Select("SELECT * FROM crm_job_log WHERE logical_run_id=#{runId} ORDER BY attempt_no DESC LIMIT 1")
    JobRun latest(String runId);

    /** 保存本次尝试并返回其独立主键。 */
    @Insert("INSERT INTO crm_job_log(job_name,business_date,logical_run_id,attempt_no,started_at,triggered_by) VALUES(#{jobName},#{businessDate},#{logicalRunId},#{attemptNo},#{startedAt},#{triggeredBy})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int start(JobRun run);

    /** 按本次主键完成日志，不能覆盖另一批次或历史尝试。 */
    @Update("UPDATE crm_job_log SET finished_at=#{finishedAt},run_status=#{runStatus},success_count=#{successCount},failure_count=#{failureCount},failed_biz_ids_json=#{failedBizIdsJson},error_summary=#{errorSummary} WHERE id=#{id} AND run_status='RUNNING'")
    int finish(JobRun run);
}
