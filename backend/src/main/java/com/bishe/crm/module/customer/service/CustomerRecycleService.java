/* 文件作用：统一公海回收规则、预警、延期审批与独立事务扫描。 */
package com.bishe.crm.module.customer.service;

import com.bishe.crm.common.api.*;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.contract.mapper.PaymentMapper;
import com.bishe.crm.module.customer.dto.RecycleRequests;
import com.bishe.crm.module.customer.entity.*;
import com.bishe.crm.module.customer.mapper.*;
import com.bishe.crm.module.customer.vo.RecycleViews;
import com.bishe.crm.module.system.mapper.AccountMapper;
import com.bishe.crm.module.task.service.JobExecutionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 预览与执行共享规则，实际回收在锁内重新检查最新业务事实。 */
@Service
public class CustomerRecycleService {
    private static final long FIRST_NOTICE_HOURS=24, FOLLOW_NOTICE_DAYS=3, EXTENSION_DAYS=7;
    private final CustomerMapper customers;
    private final RecycleMapper mapper;
    private final FollowRecordMapper follows;
    private final AccountMapper accounts;
    private final PaymentMapper payments;
    private final JobExecutionService jobs;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate transaction;

    /** 每个客户独立事务，避免一个失败回滚整个批次。 */
    public CustomerRecycleService(CustomerMapper customers,RecycleMapper mapper,FollowRecordMapper follows,AccountMapper accounts,PaymentMapper payments,JobExecutionService jobs,ObjectMapper json,Clock clock,PlatformTransactionManager manager) {
        this.customers=customers;this.mapper=mapper;this.follows=follows;this.accounts=accounts;this.payments=payments;this.jobs=jobs;this.json=json;this.clock=clock;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 所有角色查看私海客户时使用同一解释规则，不将保护细节传入公海。 */
    public RecycleViews.Rule rule(Customer c,LocalDateTime now) {
        LocalDateTime start=c.getOwnershipStartedAt()==null?c.getCreatedAt():c.getOwnershipStartedAt();
        if(start==null) start=now;
        LocalDateTime baseline=c.getLastEffectiveFollowAt();
        boolean first=baseline==null;
        LocalDateTime warning,deadline;
        if(first) {
            deadline=c.getFirstContactDeadlineAt()==null?start.plusHours(parameter("first_contact_recycle_hours",72)):c.getFirstContactDeadlineAt();
            warning=deadline.minusHours(parameter("first_contact_recycle_hours",72)-parameter("first_contact_warning_hours",48));
            baseline=deadline.minusHours(parameter("first_contact_recycle_hours",72));
        } else {
            baseline=baseline.isBefore(start)?start:baseline;
            warning=baseline.plusDays(parameter("follow_warning_days",27));deadline=baseline.plusDays(parameter("follow_recycle_days",30));
        }
        String key=c.getOwnershipSeq()+":"+(first?"FIRST:":"FOLLOW:")+baseline;
        if(key.equals(c.getRecycleExtensionKey())&&c.getRecycleExtensionUntil()!=null&&c.getRecycleExtensionUntil().isAfter(deadline)) {
            deadline=c.getRecycleExtensionUntil();warning=deadline.minusDays(3);
        }
        boolean warned=key.equals(c.getRecycleWarningKey())&&c.getRecycleWarnedAt()!=null;
        // 首次上线或停机后补发的预警仍须给足首联24小时、常规3天处理窗口。
        if(warned) {
            LocalDateTime noticeDeadline=first?c.getRecycleWarnedAt().plusHours(FIRST_NOTICE_HOURS):c.getRecycleWarnedAt().plusDays(FOLLOW_NOTICE_DAYS);
            if(noticeDeadline.isAfter(deadline)) deadline=noticeDeadline;
        }
        String protectedReason=null;
        if(!"PRIVATE".equals(c.getSeaType())||c.getOwnerId()==null) protectedReason="不属于私海客户";
        else if(c.getPermanentDealAt()!=null||"DEAL".equals(c.getCustomerStatus())) protectedReason="最终已成交，永久不自动回收";
        else if(follows.pendingEffective(c.getId())>0) protectedReason="有效跟进待核验";
        else if(mapper.openContracts(c.getId())>0) protectedReason="合同办理或履约回款尚未结束";
        else if(mapper.openOpportunities(c.getId())>0) protectedReason="存在未结束商机，须先裁决关闭或完成接管";
        else if(mapper.pendingApprovals(c.getId())>0) protectedReason="存在待处理审批或延期申请";
        else if(c.getFreezeType()!=null&&(c.getFreezeUntil()==null||c.getFreezeUntil().isAfter(now))) protectedReason="客户处于冻结保护";
        else if(mapper.handovers(c.getId(),c.getOwnershipSeq(),now)>0) protectedReason="客户交接保护期内";
        if(protectedReason!=null) return new RecycleViews.Rule(key,warning,deadline,"PROTECTED",protectedReason,warned);
        if(now.isBefore(warning)) return new RecycleViews.Rule(key,warning,deadline,"WAITING","尚未达到预警时间",warned);
        if(!warned) return new RecycleViews.Rule(key,warning,deadline,"NEED_WARNING","须先发送本轮预警，本次不能直接回收",false);
        if(now.isBefore(deadline)) return new RecycleViews.Rule(key,warning,deadline,"WARNING","已预警，尚未到回收期限",true);
        return new RecycleViews.Rule(key,warning,deadline,"ELIGIBLE",first?"首联超期，满足回收条件":"超期未跟进，满足回收条件",true);
    }

    /** 管理员查询只读预览，扫描开关关闭时不会修改客户。 */
    @Transactional(readOnly=true)
    public PageResult<RecycleViews.Row> preview(AuthPrincipal actor,RecycleRequests.Query query) {
        admin(actor);var page=mapper.preview(query.toPage(),query);
        return new PageResult<>(page.getTotal(),page.getRecords().stream().map(id->{
            var c=customers.active(id);var s=customers.summary(id,DataScope.of(actor),now());
            return new RecycleViews.Row(id,s.getCustomerName(),s.getOwnerName(),s.getTeamName(),c.getVersion(),rule(c,now()));
        }).toList());
    }

    /** 返回当前私海客户规则及本客户延期历史。 */
    @Transactional(readOnly=true)
    public RecycleViews.Detail detail(AuthPrincipal actor,Long id) {
        usable(actor);Customer c=owned(actor,id,false);var r=rule(c,now());
        var history=mapper.appeals(id).stream().map(a->{var extra=decode(a.getExtraJson());
            return new RecycleViews.Appeal(a.getId(),a.getApprovalStatus(),a.getReason(),Objects.toString(extra.get("nextPlan"),""),Objects.toString(extra.get("opinion"),""),a.getApplicantName(),a.getApproverName(),a.getSubmittedAt(),a.getProcessedAt(),a.getVersion(),reviewer(actor,c,a));
        }).toList();
        boolean due=!now().isBefore(r.warningAt())&&r.warned();
        return new RecycleViews.Detail(r,actor.id().equals(c.getOwnerId())&&due&&quota(id)&&mapper.pendingApprovals(id)==0&&c.getPermanentDealAt()==null,
            "ADMIN".equals(actor.roleCode())&&"NEED_WARNING".equals(r.decision()),history,mapper.events(id));
    }

    /** 读取常开状态及版本，供管理员应急暂停与恢复。 */
    public RecycleViews.Setting setting(AuthPrincipal actor) {admin(actor);var s=mapper.setting();return new RecycleViews.Setting(s!=null&&"true".equals(s.getConfigValue()),s==null?0:s.getVersion());}

    /** 保存应急开关和审计；正常运行保持开启，迁移显式启用已有环境。 */
    @Transactional
    public void setting(AuthPrincipal actor,RecycleRequests.Setting request) {
        admin(actor);var s=mapper.lockSetting();if(s==null) throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        changed(mapper.settingSave(s.getId(),request.version(),request.enabled().toString()));
        changed(mapper.settingAudit(s.getId(),actor.id(),now(),"自动回收"+(request.enabled()?"开启":"关闭")));
    }

    /** 自动关闭期间也可由管理员明确发送单客户预警以验证流程。 */
    @Transactional
    public void warn(AuthPrincipal actor,Long id,Long version) {
        admin(actor);Customer c=owned(actor,id,true);if(!Objects.equals(c.getVersion(),version)) throw conflict();
        var r=rule(c,now());if(!"NEED_WARNING".equals(r.decision())) throw new BizException(ErrorCode.BAD_REQUEST);
        warnOne(c,r,now(),actor.id(),null);
    }

    /** 负责人按季度一次提出延期申请，等待审核期间保护客户。 */
    @Transactional
    public Long appeal(AuthPrincipal actor,Long id,RecycleRequests.Appeal request) {
        usable(actor);Customer c=owned(actor,id,true);
        if(!actor.id().equals(c.getOwnerId())) throw new BizException(ErrorCode.FORBIDDEN);
        if(!Objects.equals(c.getVersion(),request.version())) throw conflict();
        var r=rule(c,now());
        if(!r.warned()||now().isBefore(r.warningAt())||!quota(id)||mapper.pendingApprovals(id)>0||c.getPermanentDealAt()!=null) throw new BizException(ErrorCode.BAD_REQUEST);
        var a=new RecycleAppeal();a.setBizId(id);a.setApplicantId(actor.id());a.setReason(request.reason().trim());a.setSubmittedAt(now());
        a.setExtraJson(encode(Map.of("nextPlan",request.nextPlan().trim(),"cycleKey",r.cycleKey(),"ownershipSeq",c.getOwnershipSeq(),"deadline",r.deadlineAt().toString())));
        changed(mapper.appeal(a));payments.insertApprovalTodos(a.getId(),c.getTeamId(),now().toLocalDate(),"客户预警延期申请待审核");
        audit(actor,id,"RECYCLE_APPEAL_SUBMITTED",c.getOwnershipSeq(),"申请延期："+request.reason().trim());return a.getId();
    }

    /** 非申请人的本团队经理或管理员审核；过时周期取消，不延长新周期。 */
    @Transactional
    public void review(AuthPrincipal actor,Long approvalId,RecycleRequests.Review request) {
        usable(actor);Long id=mapper.appealCustomer(approvalId);if(id==null) throw new BizException(ErrorCode.NOT_FOUND);
        Customer c=owned(actor,id,true);var a=mapper.lockAppeal(approvalId);
        if(a==null) throw new BizException(ErrorCode.NOT_FOUND);
        if(actor.id().equals(a.getApplicantId())||!("ADMIN".equals(actor.roleCode())||"MANAGER".equals(actor.roleCode())&&actor.teamId()!=null&&actor.teamId().equals(c.getTeamId()))) throw new BizException(ErrorCode.FORBIDDEN);
        if(!"PENDING".equals(a.getApprovalStatus())||!Objects.equals(a.getVersion(),request.version())) throw conflict();
        var r=rule(c,now());var extra=decode(a.getExtraJson());
        boolean stale=!r.cycleKey().equals(extra.get("cycleKey"));
        extra.put("opinion",request.opinion().trim());String status=stale?"CANCELLED":request.pass()?"APPROVED":"REJECTED";
        changed(mapper.review(approvalId,request.version(),status,actor.id(),now(),encode(extra)));
        if(request.pass()&&!stale) {
            LocalDateTime original=LocalDateTime.parse(extra.get("deadline").toString());
            LocalDateTime until=(original.isAfter(now())?original:now()).plusDays(EXTENSION_DAYS);
            changed(mapper.extend(id,c.getVersion(),r.cycleKey(),until,now()));
            mapper.resolveWarnings(id,null,now());
        }
        payments.closeApprovalTodos(approvalId,actor.id(),now());
        audit(actor,id,"RECYCLE_APPEAL_REVIEWED",c.getOwnershipSeq(),"延期审批"+status+"："+request.opinion().trim());
    }

    /** 每十分钟自动检查，保护判断直接查询最新业务事实，不依赖其他扫描顺序。 */
    @Scheduled(cron="0 */10 * * * *",zone="Asia/Shanghai")
    public void scheduledScan() {scanSelected(LocalDate.now(clock),null,null);}

    /** 管理员复用统一执行记录，关闭开关时只检查，不产生预警或回收。 */
    public Map<String,Object> scan(AuthPrincipal actor) {admin(actor);return scanSelected(LocalDate.now(clock),actor.id(),null);}

    /** 新有效跟进解决旧超期事件，阅读事实与记录仍保留。 */
    public void resolveWarnings(Long id) {mapper.resolveWarnings(id,null,now());}

    /** 每项先锁配置再锁客户，重试只接受执行器保存的失败客户。 */
    public Map<String,Object> scanSelected(LocalDate date,Long actor,Long retry) {
        return jobs.execute("customer-recycle-scan",date,actor,retry,mapper::candidates,
            id->transaction.execute(tx->{
                var setting=mapper.lockSetting();
                if(setting==null||!"true".equals(setting.getConfigValue())) return false;
                Customer c=customers.lock(id);if(c==null||!"PRIVATE".equals(c.getSeaType())) return false;
                LocalDateTime at=now();
                closeStaleAppeals(c,at);
                var r=rule(c,at);
                mapper.resolveWarnings(id,r.cycleKey(),at);
                if("NEED_WARNING".equals(r.decision())) {warnOne(c,r,at,actor,"customer-recycle-scan:"+date);return true;}
                if(!"ELIGIBLE".equals(r.decision())) return false;
                // 全部写业务均先锁客户，预览之后发生的跟进/审批会在本次规则中重新体现。
                Long owner=c.getOwnerId();accounts.lockUser(owner);
                changed(mapper.recycle(id,c.getVersion(),owner,actor,at));
                changed(accounts.syncHoldCount(owner,mapper.currentPrivateIds(owner).size()));
                mapper.resolveWarnings(id,null,at);
                changed(customers.ownershipAudit(id,"CUSTOMER_RECYCLED",actor,c.getTeamId(),at,c.getOwnershipSeq()+1,owner,null,null,null,
                    encode(Map.of("ownerId",owner,"teamId",c.getTeamId(),"lastEffectiveFollowAt",Objects.toString(c.getLastEffectiveFollowAt(),""))),"{\"seaType\":\"PUBLIC\"}",r.reason()));
                return true;
            }));
    }

    /** 新跟进或归属周期已使延期失效时，自动关闭旧申请和待办，不需经理重复处理。 */
    private void closeStaleAppeals(Customer c,LocalDateTime at) {
        String cycle=rule(c,at).cycleKey();
        for(var history:mapper.appeals(c.getId())) {
            if(!"PENDING".equals(history.getApprovalStatus())) continue;
            var a=mapper.lockAppeal(history.getId());
            if(a==null||!"PENDING".equals(a.getApprovalStatus())) continue;
            var extra=decode(a.getExtraJson());
            if(cycle.equals(extra.get("cycleKey"))) continue;
            extra.put("opinion","跟进或归属周期已更新，系统自动结束旧延期申请");
            changed(mapper.review(a.getId(),a.getVersion(),"CANCELLED",null,at,encode(extra)));
            payments.closeApprovalTodos(a.getId(),null,at);
            audit((Long)null,c.getId(),"RECYCLE_APPEAL_REVIEWED",c.getOwnershipSeq(),"旧延期申请已由系统自动结束：跟进或归属周期已更新");
        }
    }

    /** 预警记录与独立接收人待办同事务；本次发送后不立刻回收。 */
    private void warnOne(Customer c,RecycleViews.Rule r,LocalDateTime at,Long actor,String runId) {
        changed(mapper.warn(c.getId(),c.getVersion(),r.cycleKey(),at));mapper.resolveWarnings(c.getId(),r.cycleKey(),at);
        mapper.warningTodos(c.getId(),r.cycleKey(),at.toLocalDate(),c.getOwnerId(),c.getTeamId(),runId);
        LocalDateTime noticeDeadline=c.getLastEffectiveFollowAt()==null?at.plusHours(FIRST_NOTICE_HOURS):at.plusDays(FOLLOW_NOTICE_DAYS);
        LocalDateTime effectiveDeadline=noticeDeadline.isAfter(r.deadlineAt())?noticeDeadline:r.deadlineAt();
        audit(actor,c.getId(),"CUSTOMER_RECYCLE_WARNING",c.getOwnershipSeq(),"已发送预警；回收期限："+effectiveDeadline.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
    /** 当前客户归属决定读取与审批权限，不接受历史交接账号办理延期。 */
    private Customer owned(AuthPrincipal actor,Long id,boolean lock) {
        Customer c=lock?customers.lock(id):customers.active(id);
        if(c==null||!"PRIVATE".equals(c.getSeaType())||(!"ADMIN".equals(actor.roleCode())&&!("MANAGER".equals(actor.roleCode())&&actor.teamId()!=null&&actor.teamId().equals(c.getTeamId()))&&!actor.id().equals(c.getOwnerId()))) throw new BizException(ErrorCode.NOT_FOUND);
        return c;
    }
    /** 当前团队管理者不能审核自己提交的申请。 */
    private boolean reviewer(AuthPrincipal actor,Customer c,RecycleAppeal a) {return a!=null&&"PENDING".equals(a.getApprovalStatus())&&!actor.id().equals(a.getApplicantId())&&("ADMIN".equals(actor.roleCode())||"MANAGER".equals(actor.roleCode())&&actor.teamId()!=null&&actor.teamId().equals(c.getTeamId()));}
    /** 客户本季度最多一次，不因换负责人或驳回重置。 */
    private boolean quota(Long id) {LocalDateTime at=now();LocalDateTime start=LocalDate.of(at.getYear(),((at.getMonthValue()-1)/3)*3+1,1).atStartOfDay();return mapper.quarterAppeals(id,start,start.plusMonths(3))==0;}
    /** 读取受控时间参数，配置异常时阻止任务而非缩短客户期限。 */
    private long parameter(String key,long fallback) {String value=mapper.parameter(key);long n=value==null?fallback:Long.parseLong(value);if(n<=0||n>3660) throw new BizException(ErrorCode.BAD_REQUEST);return n;}
    /** 审计与对应业务使用同事务，系统任务的操作人可为空。 */
    private void audit(AuthPrincipal actor,Long id,String action,Long seq,String reason) {audit(actor==null?null:actor.id(),id,action,seq,reason);}
    /** 统一记录系统及手工回收操作。 */
    private void audit(Long actor,Long id,String action,Long seq,String reason) {changed(customers.audit(id,action,actor,null,now(),seq,null,null,reason));}
    /** 结构化审批快照只在服务器内读取。 */
    private Map<String,Object> decode(String s) {try{return json.readValue(s,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new BizException(ErrorCode.CONFLICT);}}
    /** 序列化失败必须回滚业务。 */
    private String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    /** 使用统一业务时钟，便于时间边界测试。 */
    private LocalDateTime now() {return LocalDateTime.now(clock);}
    /** 版本条件更新必须影响一行。 */
    private void changed(int count) {if(count!=1) throw conflict();}
    /** 返回统一并发冲突。 */
    private BizException conflict() {return new BizException(ErrorCode.CONFLICT);}
    /** 完成首次改密才能使用业务。 */
    private void usable(AuthPrincipal a) {if(a==null||a.mustChangePassword())throw new BizException(ErrorCode.PASSWORD_CHANGE_REQUIRED);}
    /** 全局回收管理仅管理员。 */
    private void admin(AuthPrincipal a) {usable(a);if(!"ADMIN".equals(a.roleCode()))throw new BizException(ErrorCode.FORBIDDEN);}
}
