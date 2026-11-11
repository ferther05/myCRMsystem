/* 文件作用：提供本人待办、授权业务跳转、审计摘要与管理员任务管理。 */
package com.bishe.crm.module.task.service;

import com.bishe.crm.common.api.*;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.contract.mapper.PaymentMapper;
import com.bishe.crm.module.contract.service.PaymentOverdueJob;
import com.bishe.crm.module.customer.mapper.CustomerMapper;
import com.bishe.crm.module.opportunity.mapper.OpportunityMapper;
import com.bishe.crm.module.opportunity.service.OpportunityActivityService;
import com.bishe.crm.module.task.dto.TaskQuery;
import com.bishe.crm.module.task.entity.*;
import com.bishe.crm.module.task.mapper.TaskMapper;
import com.bishe.crm.module.task.vo.TaskViews;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 页面按钮不是权限边界，所有入口再次核对身份与业务范围。 */
@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskMapper mapper;
    private final CustomerMapper customers;
    private final OpportunityMapper opportunities;
    private final PaymentMapper payments;
    private final OpportunityActivityService activity;
    private final PaymentOverdueJob overdue;
    private final Clock clock;
    private final BusinessReminderService reminders;
    private final com.bishe.crm.module.customer.service.CustomerRecycleService recycle;

    /** 导航角标只表示本人收到的未读提醒和待办，不扩大到团队数据。 */
    @Transactional(readOnly=true)
    public TaskViews.Summary summary(AuthPrincipal actor) {
        usable(actor);
        return mapper.summary(actor.id());
    }

    /** 本人待办分页；归属改变后不再暴露旧业务名称或可点击位置。 */
    @Transactional(readOnly=true)
    public PageResult<TaskViews.TodoRow> todos(AuthPrincipal actor, TaskQuery query) {
        usable(actor); dates(query);
        var page=mapper.todos(query.toPage(),actor.id(),query);
        return new PageResult<>(page.getTotal(),page.getRecords().stream().map(t->{
            TaskViews.Target target=target(actor,t.getBizType(),t.getBizId());
            return new TaskViews.TodoRow(t.getId(),t.getTodoType(),t.getBizType(),t.getBizId(),target==null?"关联业务已不可访问":t.getTitle(),
                t.getTodoStatus(),t.getRemindDate(),t.getCreatedAt(),t.getReadAt(),t.getCompletedAt(),t.getResolvedAt(),t.getVersion(),target);
        }).toList());
    }

    /** 只改已读标记；更新和审计在同一事务内完成。 */
    @Transactional
    public void read(AuthPrincipal actor, Long id, Long version) {
        usable(actor);requireTodo(actor,id);
        if(mapper.read(id,actor.id(),version,LocalDateTime.now(clock))!=1) throw new BizException(ErrorCode.CONFLICT);
        mapper.audit("TODO",id,"TODO_READ",actor.id(),actor.teamId(),LocalDateTime.now(clock),"已阅读提醒，业务状态保持不变");
    }

    /** 点击提醒时重新授权，不能利用历史链接越权访问。 */
    @Transactional(readOnly=true)
    public TaskViews.Target link(AuthPrincipal actor, Long id) {
        usable(actor); Todo t=requireTodo(actor,id);
        TaskViews.Target target=target(actor,t.getBizType(),t.getBizId());
        if(target==null) throw new BizException(ErrorCode.NOT_FOUND);
        return target;
    }

    /** 按当前业务归属查询摘要，不返回敏感的原始变化快照。 */
    @Transactional(readOnly=true)
    public PageResult<TaskViews.Operation> operations(AuthPrincipal actor, TaskQuery query) {
        usable(actor);dates(query);
        return PageResult.from(mapper.operations(query.toPage(),DataScope.of(actor),query));
    }

    /** 管理员查看执行日志，普通经理无权通过此入口管理全局扫描。 */
    public PageResult<JobRun> jobs(AuthPrincipal actor, TaskQuery query) {
        admin(actor);dates(query);return PageResult.from(mapper.jobs(query.toPage(),query));
    }

    /** 管理员扫描今天的已实现业务；不允许请求任意历史日期全量改写业务。 */
    public Map<String,Object> scan(AuthPrincipal actor,String name) {
        admin(actor);return dispatch(name,LocalDate.now(clock),actor.id(),null);
    }

    /** 补跑主键来自服务器保存的最新失败记录，禁止客户端传失败对象清单。 */
    public Map<String,Object> retry(AuthPrincipal actor,Long id) {
        admin(actor); JobRun source=mapper.job(id);
        if(source==null) throw new BizException(ErrorCode.NOT_FOUND);
        return dispatch(source.getJobName(),source.getBusinessDate(),actor.id(),id);
    }

    /** 已实现扫描复用同一执行器，统一日志和幂等边界。 */
    private Map<String,Object> dispatch(String name,LocalDate date,Long actor,Long retry) {
        return switch(name) {
            case "opportunity-activity-scan" -> activity.scanSelected(date,actor,retry);
            case "payment-overdue-scan" -> overdue.scanSelected(date,actor,retry);
            case "customer-recycle-scan" -> recycle.scanSelected(date,actor,retry);
            case "business-reminder-scan" -> reminders.scan(date,actor,retry);
            default -> throw new BizException(ErrorCode.BAD_REQUEST);
        };
    }

    /** 生成白名单应用内路径，每类对象使用其当前授权范围。 */
    private TaskViews.Target target(AuthPrincipal actor,String type,Long id) {
        DataScope scope=DataScope.of(actor);
        if("CUSTOMER".equals(type)) {
            var c=customers.summary(id,scope,LocalDateTime.now(clock));
            return c==null?null:new TaskViews.Target("/customers?id="+id,"客户详情");
        }
        if("OPPORTUNITY".equals(type)) {
            var o=opportunities.summary(id,scope);
            return o==null?null:new TaskViews.Target("/opportunities?id="+id,"商机详情");
        }
        if("PAYMENT_PERIOD".equals(type)) {
            Long contract=mapper.periodContract(id);
            return contract==null?null:target(actor,"CONTRACT",contract);
        }
        if("CONTRACT".equals(type)) return payments.scopedContract(id,scope)==null?null:new TaskViews.Target("/contracts?id="+id,"合同及回款");
        if("APPROVAL".equals(type)) {
            Map<String,Object> a=mapper.approval(id);
            if(a==null) return null;
            if("CUSTOMER".equals(a.get("biz_type"))) return target(actor,"CUSTOMER",((Number)a.get("biz_id")).longValue());
            if("CONTRACT".equals(a.get("biz_type"))) return target(actor,"CONTRACT",((Number)a.get("biz_id")).longValue());
            if("ACCOUNT_OPEN".equals(a.get("approval_type")) && ("ADMIN".equals(actor.roleCode()) || "MANAGER".equals(actor.roleCode()) && Objects.equals(actor.id(),((Number)a.get("applicant_id")).longValue()))) return new TaskViews.Target("/approvals","账号申请审批");
        }
        return null;
    }

    /** 待办所有权不等同于关联业务的当前访问权限。 */
    private Todo requireTodo(AuthPrincipal actor,Long id) { Todo t=mapper.todo(id,actor.id());if(t==null) throw new BizException(ErrorCode.NOT_FOUND);return t; }
    /** 普通筛选不接受倒序时间区间。 */
    private void dates(TaskQuery q) { if(q.getFrom()!=null&&q.getTo()!=null&&q.getFrom().isAfter(q.getTo())) throw new BizException(ErrorCode.VALIDATION_ERROR); }
    /** 首次改密完成后才能使用业务入口。 */
    private void usable(AuthPrincipal a) { if(a==null||a.mustChangePassword()) throw new BizException(ErrorCode.PASSWORD_CHANGE_REQUIRED); }
    /** 全局扫描和执行日志仅管理员可用。 */
    private void admin(AuthPrincipal a) { usable(a);if(!"ADMIN".equals(a.roleCode())) throw new BizException(ErrorCode.FORBIDDEN); }
}
