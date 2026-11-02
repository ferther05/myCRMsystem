/* 文件作用：回收判定所需的业务事实、预警、延期审批和条件更新。 */
package com.bishe.crm.module.customer.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bishe.crm.module.customer.dto.RecycleRequests;
import com.bishe.crm.module.customer.entity.RecycleAppeal;
import com.bishe.crm.module.system.entity.SystemConfig;
import com.bishe.crm.module.customer.vo.RecycleViews;
import java.time.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 业务变更均由服务层在客户行锁内调用。 */
@Mapper
public interface RecycleMapper {
    /** 读取真实配置，缺失时不自动启用。 */
    @Select("SELECT * FROM sys_config WHERE config_type='PARAM' AND config_group='crm.recycle' AND config_key='enabled'")
    SystemConfig setting();
    /** 开关与扫描条目使用同一锁，关闭返回后不再有新回收提交。 */
    @Select("SELECT * FROM sys_config WHERE config_type='PARAM' AND config_group='crm.recycle' AND config_key='enabled' FOR UPDATE")
    SystemConfig lockSetting();
    /** 配置版本防止管理员互相覆盖。 */
    @Update("UPDATE sys_config SET config_value=#{value},version=version+1 WHERE id=#{id} AND version=#{version}")
    int settingSave(@Param("id") Long id,@Param("version") Long version,@Param("value") String value);
    /** 系统开关审计关联真实配置ID，不伪装成客户0。 */
    @Insert("INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,occurred_at,reason) VALUES('SYSTEM_CONFIG',#{id},'RECYCLE_SETTING_CHANGED',#{actor},#{now},#{reason})")
    int settingAudit(@Param("id") Long id,@Param("actor") Long actor,@Param("now") LocalDateTime now,@Param("reason") String reason);
    /** 读取已有统一时间参数。 */
    @Select("SELECT config_value FROM sys_config WHERE config_type='PARAM' AND config_group='crm.time' AND config_key=#{key} AND enabled=1")
    String parameter(String key);
    /** 全部私海候选，仅提供服务器内部扫描。 */
    @Select("SELECT id FROM crm_customer WHERE deleted=0 AND sea_type='PRIVATE' AND owner_id IS NOT NULL ORDER BY id")
    List<Long> candidates();
    /** 持有量必须使用锁定当前读，避免规则查询建立的旧快照漏掉并发领取。 */
    @Select("SELECT id FROM crm_customer WHERE owner_id=#{owner} AND deleted=0 AND sea_type='PRIVATE' ORDER BY id FOR UPDATE")
    List<Long> currentPrivateIds(Long owner);
    /** 管理员预览先分页再解释规则，不在内存过滤后伪造total。 */
    @Select("<script>SELECT id FROM crm_customer WHERE deleted=0 AND sea_type='PRIVATE' AND owner_id IS NOT NULL <if test='q.keyword != null and q.keyword != &quot;&quot;'>AND customer_name LIKE CONCAT('%',#{q.keyword},'%')</if> ORDER BY id DESC</script>")
    IPage<Long> preview(Page<Long> page,@Param("q") RecycleRequests.Query q);
    /** 未结束商机必须先裁决或交接；停滞不能直接丢失业务归属。 */
    @Select("SELECT COUNT(*) FROM crm_opportunity WHERE customer_id=#{id} AND stage_code NOT IN ('LOST','CLOSED')")
    long openOpportunities(Long id);
    /** 未结束合同或尚有余额一律保护，包括登记/签署办理阶段。 */
    @Select("SELECT COUNT(*) FROM crm_contract WHERE customer_id=#{id} AND (contract_status NOT IN ('TERMINATED','VOID','SETTLED') OR outstanding_amount>0)")
    long openContracts(Long id);
    /** 保护当前客户及其业务的所有待处理审批，不依赖冻结字段是否及时清理。 */
    @Select("SELECT COUNT(*) FROM crm_approval a WHERE a.approval_status='PENDING' AND ((a.biz_type='CUSTOMER' AND a.biz_id=#{id}) OR (a.biz_type='CONTRACT' AND EXISTS(SELECT 1 FROM crm_contract c WHERE c.id=a.biz_id AND c.customer_id=#{id})) OR (a.biz_type='OPPORTUNITY' AND EXISTS(SELECT 1 FROM crm_opportunity o WHERE o.id=a.biz_id AND o.customer_id=#{id})))")
    long pendingApprovals(Long id);
    /** 已生成的当前轮交接只读期间不自动回收。 */
    @Select("SELECT COUNT(*) FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=#{id} AND ownership_seq=#{seq} AND handover_readonly_until>#{now}")
    long handovers(@Param("id") Long id,@Param("seq") Long seq,@Param("now") LocalDateTime now);
    /** 同季度按客户限一次，拒绝后重提也不绕过次数。 */
    @Select("SELECT COUNT(*) FROM crm_approval WHERE biz_type='CUSTOMER' AND biz_id=#{id} AND approval_type='RECYCLE_APPEAL' AND submitted_at>=#{start} AND submitted_at<#{end}")
    long quarterAppeals(@Param("id") Long id,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
    /** 保存当前周期的一次预警事实。 */
    @Update("UPDATE crm_customer SET recycle_warning_key=#{key},recycle_warned_at=#{now},version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version} AND sea_type='PRIVATE' AND deleted=0")
    int warn(@Param("id") Long id,@Param("version") Long version,@Param("key") String key,@Param("now") LocalDateTime now);
    /** 发送专员、团队经理的独立预警，不向其他专员暴露客户信息。 */
    @Insert("INSERT INTO crm_todo(todo_type,biz_type,biz_id,occurrence_key,remind_date,recipient_id,title,source_run_id) SELECT 'CUSTOMER_WARNING','CUSTOMER',#{id},#{key},#{date},u.id,'客户即将达到公海回收期限',#{runId} FROM sys_user u WHERE u.account_status='ACTIVE' AND (u.id=#{owner} OR (u.role_code='MANAGER' AND u.team_id=#{team})) ON DUPLICATE KEY UPDATE id=crm_todo.id")
    int warningTodos(@Param("id") Long id,@Param("key") String key,@Param("date") LocalDate date,@Param("owner") Long owner,@Param("team") Long team,@Param("runId") String runId);
    /** 有新周期或业务结束时解除旧预警，不修改已读历史。 */
    @Update("UPDATE crm_todo SET todo_status='RESOLVED',resolved_at=#{now},version=version+1 WHERE biz_type='CUSTOMER' AND biz_id=#{id} AND todo_type='CUSTOMER_WARNING' AND todo_status='PENDING' AND (#{key} IS NULL OR occurrence_key<>#{key})")
    int resolveWarnings(@Param("id") Long id,@Param("key") String keepKey,@Param("now") LocalDateTime now);
    /** 归属与期限复核后原子回收，保留最后跟进与历史业务数据。 */
    @Update("UPDATE crm_customer SET previous_owner_id=owner_id,return_priority_until=#{now}+INTERVAL 7 DAY,owner_id=NULL,team_id=NULL,sea_type='PUBLIC',customer_status='NEW',ownership_seq=ownership_seq+1,version=version+1,updated_by=#{actor},updated_at=#{now},freeze_type=NULL,freeze_until=NULL,freeze_approval_id=NULL WHERE id=#{id} AND version=#{version} AND sea_type='PRIVATE' AND owner_id=#{owner} AND deleted=0 AND permanent_deal_at IS NULL")
    int recycle(@Param("id") Long id,@Param("version") Long version,@Param("owner") Long owner,@Param("actor") Long actor,@Param("now") LocalDateTime now);
    /** 客户行锁内创建延期审批，返回独立主键。 */
    @Insert("INSERT INTO crm_approval(approval_type,biz_type,biz_id,applicant_id,reason,submitted_at,extra_json) VALUES('RECYCLE_APPEAL','CUSTOMER',#{bizId},#{applicantId},#{reason},#{submittedAt},#{extraJson})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int appeal(RecycleAppeal appeal);
    /** 锁定单条延期审批。 */
    @Select("SELECT * FROM crm_approval WHERE id=#{id} AND biz_type='CUSTOMER' AND approval_type='RECYCLE_APPEAL' FOR UPDATE")
    RecycleAppeal lockAppeal(Long id);
    /** 从审批ID定位客户，锁定顺序始终先客户后审批。 */
    @Select("SELECT biz_id FROM crm_approval WHERE id=#{id} AND biz_type='CUSTOMER' AND approval_type='RECYCLE_APPEAL'")
    Long appealCustomer(Long id);
    /** 返回客户审批历史及人员名称。 */
    @Select("SELECT a.*,u.real_name applicant_name,r.real_name approver_name FROM crm_approval a JOIN sys_user u ON u.id=a.applicant_id LEFT JOIN sys_user r ON r.id=a.approver_id WHERE a.biz_type='CUSTOMER' AND a.biz_id=#{id} AND a.approval_type='RECYCLE_APPEAL' ORDER BY a.id DESC")
    List<RecycleAppeal> appeals(Long id);
    /** 当前有权查看客户者可追溯归属生命周期，不返回原始敏感快照。 */
    @Select("SELECT l.id,l.action_code action,COALESCE(u.real_name,u.username,'系统任务') operator_name,l.occurred_at,l.reason FROM crm_operation_log l LEFT JOIN sys_user u ON u.id=l.operator_id WHERE l.biz_type='CUSTOMER' AND l.biz_id=#{id} AND l.action_code IN ('CUSTOMER_RECYCLE_WARNING','RECYCLE_APPEAL_SUBMITTED','RECYCLE_APPEAL_REVIEWED','CUSTOMER_RECYCLED','PUBLIC_CLAIM','PRIORITY_CLAIM') ORDER BY l.id DESC")
    List<RecycleViews.Event> events(Long id);
    /** 已处理/旧版本不能重复审批。 */
    @Update("UPDATE crm_approval SET approval_status=#{status},approver_id=#{actor},processed_at=#{now},extra_json=#{extra},version=version+1 WHERE id=#{id} AND version=#{version} AND approval_status='PENDING'")
    int review(@Param("id") Long id,@Param("version") Long version,@Param("status") String status,@Param("actor") Long actor,@Param("now") LocalDateTime now,@Param("extra") String extra);
    /** 延期绑定当前跟进周期，不污染以后重新领取的客户。 */
    @Update("UPDATE crm_customer SET recycle_extension_key=#{key},recycle_extension_until=#{until},recycle_warning_key=NULL,recycle_warned_at=NULL,version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version} AND sea_type='PRIVATE'")
    int extend(@Param("id") Long id,@Param("version") Long version,@Param("key") String key,@Param("until") LocalDateTime until,@Param("now") LocalDateTime now);
}
