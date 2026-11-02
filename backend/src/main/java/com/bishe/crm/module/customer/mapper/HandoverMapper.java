/* 文件作用：锁定批量交接关联业务并同步当前负责人，历史作者及业绩快照不修改。 */
package com.bishe.crm.module.customer.mapper;

import java.util.*;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;

@Mapper
public interface HandoverMapper {
    /** 离职账号仍可作为移出方；只返回管理员需要的最小账号资料。 */
    @Select("SELECT u.id,u.username,u.real_name,u.team_id,t.team_name,u.role_code,u.account_status FROM sys_user u JOIN sys_team t ON t.id=u.team_id WHERE u.role_code IN ('SPECIALIST','MANAGER') ORDER BY u.team_id,u.id")
    List<Map<String,Object>> owners();
    /** 待选客户，前端每页十条；批次最多选择50个。 */
    @Select("SELECT id,customer_name,owner_id,team_id,version FROM crm_customer WHERE owner_id=#{owner} AND sea_type='PRIVATE' AND deleted=0 ORDER BY id LIMIT 501")
    List<Map<String,Object>> candidates(Long owner);
    /** 按主键顺序锁定全部商机，包含终态；不移动任何历史跟进作者。 */
    @Select("SELECT id,owner_id,team_id,version FROM crm_opportunity WHERE customer_id=#{id} ORDER BY id FOR UPDATE")
    List<Map<String,Object>> opportunities(Long id);
    /** 合同当前归属随客户交接，签约/到账业绩快照保留。 */
    @Select("SELECT id,owner_id,team_id,version FROM crm_contract WHERE customer_id=#{id} ORDER BY id FOR UPDATE")
    List<Map<String,Object>> contracts(Long id);
    /** 只改变当前归属与版本，保持阶段、金额、计时与历史不变。 */
    @Update("UPDATE crm_opportunity SET owner_id=#{owner},team_id=#{team},version=version+1 WHERE id=#{id} AND version=#{version}")
    int moveOpportunity(@Param("id") Long id,@Param("version") Long version,@Param("owner") Long owner,@Param("team") Long team);
    @Update("UPDATE crm_contract SET owner_id=#{owner},team_id=#{team},version=version+1 WHERE id=#{id} AND version=#{version}")
    int moveContract(@Param("id") Long id,@Param("version") Long version,@Param("owner") Long owner,@Param("team") Long team);
    /** 移交双方退出原协作关系，避免旧负责人借协作权限继续写入。其他协作人不受影响。 */
    @Update("UPDATE crm_opportunity_collaborator SET collaborator_status='LEFT',left_at=#{now},updated_at=#{now},version=version+1 WHERE opportunity_id=#{id} AND user_id IN(#{source},#{target}) AND collaborator_status='ACTIVE'")
    int endOwnerCollaboration(@Param("id") Long id,@Param("source") Long source,@Param("target") Long target,@Param("now") LocalDateTime now);
    String RELATED="(t.biz_type='CUSTOMER' AND t.biz_id=#{id} OR t.biz_type='OPPORTUNITY' AND t.biz_id IN(SELECT id FROM crm_opportunity WHERE customer_id=#{id}) OR t.biz_type='CONTRACT' AND t.biz_id IN(SELECT id FROM crm_contract WHERE customer_id=#{id}) OR t.biz_type='PAYMENT_PERIOD' AND t.biz_id IN(SELECT pp.id FROM crm_payment_period pp JOIN crm_payment_plan p ON p.id=pp.plan_id JOIN crm_contract k ON k.id=p.contract_id WHERE k.customer_id=#{id}) OR t.biz_type='APPROVAL' AND t.biz_id IN(SELECT a.id FROM crm_approval a WHERE a.biz_type='CUSTOMER' AND a.biz_id=#{id} OR a.biz_type='CONTRACT' AND a.biz_id IN(SELECT id FROM crm_contract WHERE customer_id=#{id}) OR a.biz_type='OPPORTUNITY' AND a.biz_id IN(SELECT id FROM crm_opportunity WHERE customer_id=#{id})))";
    /** 仅移交原负责人的未处理事项，经理审批待办保持原接收人。 */
    @Select("SELECT t.id,t.version FROM crm_todo t WHERE t.recipient_id=#{owner} AND t.todo_status='PENDING' AND "+RELATED+" ORDER BY t.id FOR UPDATE")
    List<Map<String,Object>> todos(@Param("id") Long id,@Param("owner") Long owner);
    /** 新负责人收到未读待办；已存在同一事件时不新增重复行。 */
    @Insert("INSERT INTO crm_todo(todo_type,biz_type,biz_id,occurrence_key,remind_date,recipient_id,title,source_run_id) SELECT src.todo_type,src.biz_type,src.biz_id,src.occurrence_key,src.remind_date,#{owner},src.title,src.source_run_id FROM crm_todo src WHERE src.id=#{id} AND src.todo_status='PENDING' ON DUPLICATE KEY UPDATE todo_status='PENDING',read_at=NULL,resolved_at=NULL,completed_at=NULL,version=crm_todo.version+1")
    int copyTodo(@Param("id") Long id,@Param("owner") Long owner);
    @Update("UPDATE crm_todo SET todo_status='CANCELLED',resolved_at=#{now},version=version+1 WHERE id=#{id} AND version=#{version} AND todo_status='PENDING'")
    int cancelTodo(@Param("id") Long id,@Param("version") Long version,@Param("now") LocalDateTime now);
}
