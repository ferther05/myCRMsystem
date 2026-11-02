/* 文件作用：查询最新跟进计划和合同办理进度，维护每日去重待办。 */
package com.bishe.crm.module.task.mapper;

import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface BusinessReminderMapper {
    /** 包含已有提醒的客户，确保业务结束或归属变化后也能解除旧提醒。 */
    @Select("SELECT id FROM crm_customer WHERE deleted=0 AND sea_type='PRIVATE' UNION SELECT biz_id FROM crm_todo WHERE biz_type='CUSTOMER' AND todo_type IN ('FOLLOW_DUE','CARE_BIRTHDAY') AND todo_status='PENDING' UNION SELECT k.customer_id FROM crm_contract k JOIN crm_todo t ON t.biz_type='CONTRACT' AND t.biz_id=k.id WHERE t.todo_type IN ('CONTRACT_STAGNANT','CONTRACT_REVIEW') AND t.todo_status='PENDING'")
    List<Long> candidates();
    /** 在客户锁内按跟进对象挑选最新记录；新跟进即替代旧计划。 */
    @Select("<script>SELECT f.id,f.opportunity_id,f.next_action_at FROM crm_follow_record f LEFT JOIN crm_opportunity o ON o.id=f.opportunity_id WHERE f.customer_id=#{id} AND f.review_status&lt;&gt;'RETURNED' AND f.next_action_at IS NOT NULL AND f.next_action_at&lt;=#{now} AND (f.opportunity_id IS NULL OR o.stage_code NOT IN ('WON','LOST','CLOSED')) AND NOT EXISTS(SELECT 1 FROM crm_follow_record n WHERE n.customer_id=f.customer_id AND n.opportunity_id&lt;=&gt;f.opportunity_id AND n.review_status&lt;&gt;'RETURNED' AND (n.followed_at&gt;f.followed_at OR n.followed_at=f.followed_at AND n.id&gt;f.id))</script>")
    List<Map<String,Object>> dueFollows(@Param("id") Long id,@Param("now") LocalDateTime now);
    /** 只对未完成且正常办理中的合同提醒；按最近办理动作而非财务更新计时。 */
    @Select("<script>SELECT k.id FROM crm_contract k WHERE k.customer_id=#{id} AND k.signing_progress NOT IN ('FILE_CONFIRMED','EXCEPTION','WITHDRAWN') AND k.contract_status NOT IN ('SIGNED','PERFORMING','SETTLED','TERMINATED') AND GREATEST(k.created_at,COALESCE((SELECT MAX(l.occurred_at) FROM crm_operation_log l WHERE l.biz_type='CONTRACT' AND l.biz_id=k.id AND l.action_code LIKE 'CONTRACT_%' AND (l.before_json IS NULL OR l.after_json IS NULL OR NOT(JSON_REMOVE(l.before_json,'$.version') &lt;=&gt; JSON_REMOVE(l.after_json,'$.version')))),k.created_at))&lt;=#{cutoff}</script>")
    List<Long> stagnantContracts(@Param("id") Long id,@Param("cutoff") LocalDateTime cutoff);
    /** 当前启用的负责人和经理，不向停用账号继续派发。 */
    @Select("SELECT u.id FROM sys_user u WHERE u.account_status='ACTIVE' AND (u.id=#{owner} OR (#{manager}=true AND u.team_id=#{team} AND u.role_code='MANAGER'))")
    List<Long> recipients(@Param("owner") Long owner,@Param("team") Long team,@Param("manager") boolean manager);
    /** 仅将已不满足条件、旧日期或旧接收人的待办解除，避免重复扫描抹掉已读。 */
    @Update("<script>UPDATE crm_todo SET todo_status='RESOLVED',resolved_at=#{now},version=version+1 WHERE todo_type=#{type} AND biz_type=#{biz} AND biz_id=#{id} AND todo_status='PENDING' <if test='keys.size > 0 and users.size > 0'>AND (remind_date&lt;&gt;#{date} OR occurrence_key NOT IN <foreach collection='keys' item='v' open='(' close=')' separator=','>#{v}</foreach> OR recipient_id NOT IN <foreach collection='users' item='v' open='(' close=')' separator=','>#{v}</foreach>)</if></script>")
    int resolve(@Param("type") String type,@Param("biz") String biz,@Param("id") Long id,@Param("keys") List<String> keys,@Param("users") List<Long> users,@Param("date") LocalDate date,@Param("now") LocalDateTime now);
    /** 同一来源同日同接收人只保留一条；重新成为待处理时恢复未读。 */
    @Insert("INSERT INTO crm_todo(todo_type,biz_type,biz_id,occurrence_key,remind_date,recipient_id,title,source_run_id) VALUES(#{type},#{biz},#{id},#{key},#{date},#{user},#{title},#{run}) ON DUPLICATE KEY UPDATE read_at=IF(todo_status='PENDING',read_at,NULL),resolved_at=IF(todo_status='PENDING',resolved_at,NULL),version=version+IF(todo_status='PENDING',0,1),todo_status='PENDING'")
    int upsert(@Param("type") String type,@Param("biz") String biz,@Param("id") Long id,@Param("key") String key,@Param("date") LocalDate date,@Param("user") Long user,@Param("title") String title,@Param("run") String run);
    /** 所有合同均参与旧提醒清理，办理完成后不继续提醒。 */
    @Select("SELECT id FROM crm_contract WHERE customer_id=#{id} ORDER BY id")
    List<Long> contracts(Long id);
    /** 已有联系人生日当天关怀，不虚构周年日；2月29日仅在闰年当天触发。 */
    @Select("SELECT id FROM crm_contact WHERE customer_id=#{id} AND MONTH(birthday)=MONTH(#{date}) AND DAY(birthday)=DAY(#{date})")
    List<Long> birthdays(@Param("id") Long id,@Param("date") LocalDate date);
    /** 两次合同核验都有可办理待办；异常/撤回时不继续催审。 */
    @Select("SELECT id,signing_progress,owner_id,team_id FROM crm_contract WHERE customer_id=#{id} AND signing_progress IN ('DRAFT_REVIEW','FILE_REVIEW')")
    List<Map<String,Object>> reviews(Long id);
    /** 本团队非负责经理优先核验；没有合适经理时交管理员，禁止自审。 */
    @Select("SELECT id FROM sys_user WHERE team_id=#{team} AND role_code='MANAGER' AND account_status='ACTIVE' AND id!=#{owner}")
    List<Long> reviewers(@Param("team") Long team,@Param("owner") Long owner);
    @Select("SELECT id FROM sys_user WHERE role_code='ADMIN' AND account_status='ACTIVE' AND id!=#{owner}")
    List<Long> administrators(Long owner);
}
