package com.bishe.crm.module.opportunity.service;

import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.customer.mapper.CustomerMapper;
import com.bishe.crm.module.opportunity.entity.Opportunity;
import com.bishe.crm.module.opportunity.mapper.OpportunityMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class OpportunityActivityService {
    private final OpportunityMapper opportunities;
    private final CustomerMapper customers;
    private final Clock businessClock;
    private final TransactionTemplate transaction;

    public OpportunityActivityService(OpportunityMapper opportunities,CustomerMapper customers,Clock businessClock,PlatformTransactionManager manager) {
        this.opportunities=opportunities; this.customers=customers; this.businessClock=businessClock;
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(cron="0 15 1 * * *",zone="Asia/Shanghai")
    public void scheduledScan() { scanNow(null); }

    // 单实例内串行运行；每条商机独立提交，失败不会回滚其他商机。
    public synchronized Map<String,Object> scanNow(Long actorId) {
        LocalDateTime now=LocalDateTime.now(businessClock);
        String runId="opportunity-activity-scan:"+now.toLocalDate();
        opportunities.startRun(now.toLocalDate(),runId,now,actorId);
        int success=0,changed=0; List<Long> failed=new ArrayList<>();
        for (Long id:opportunities.activityCandidates()) {
            try {
                Boolean updated=transaction.execute(status -> scanOne(id,now,runId,actorId));
                if (Boolean.TRUE.equals(updated)) changed++;
                success++;
            } catch (RuntimeException e) { failed.add(id); log.error("商机扫描失败 id={}",id,e); }
        }
        opportunities.finishRun(runId,LocalDateTime.now(businessClock),failed.isEmpty()?"SUCCESS":"PARTIAL_FAILURE",success,failed.size(),failed.toString());
        return Map.of("runId",runId,"processed",success,"changed",changed,"failedIds",failed);
    }

    private boolean scanOne(Long id,LocalDateTime now,String runId,Long actorId) {
        Opportunity found=opportunities.find(id);
        if(found==null || customers.lock(found.getCustomerId())==null) return false;
        Opportunity o=opportunities.lock(id);
        if(o==null || !List.of("NEW","FOLLOWING","QUOTED","DEAL").contains(o.getStageCode()) || opportunities.activeContracts(id)>0) return false;
        // lastEffectiveProgressAt 由阶段动作、有效跟进及打回重算统一维护。
        // 不能再取自动 FOLLOWING 的阶段时间，否则已打回的跟进仍会延长计时。
        LocalDateTime baseline=o.getLastEffectiveProgressAt() == null ? o.getStageChangedAt() : o.getLastEffectiveProgressAt();
        if(baseline==null) baseline=o.getCreatedAt();
        long age=Math.max(0,Duration.between(baseline,now).toDays());
        int threshold=o.getStagnationDays(),grace=o.getGraceDays();
        String next=age<threshold?"ACTIVE":age<threshold+grace?"STAGNANT":"CLOSE_PENDING";
        LocalDateTime pending="CLOSE_PENDING".equals(next)?(o.getClosePendingAt()==null?now:o.getClosePendingAt()):null;
        boolean changed=!next.equals(o.getActivityStatus()) || !Objects.equals(pending,o.getClosePendingAt());
        if(changed) {
            if(opportunities.updateActivity(id,o.getVersion(),next,pending,now)!=1) throw new BizException(ErrorCode.CONFLICT);
            opportunities.audit(id,"OPPORTUNITY_ACTIVITY_CHANGED",actorId,o.getTeamId(),now,
                    "{\"activityStatus\":\""+o.getActivityStatus()+"\"}","{\"activityStatus\":\""+next+"\"}","系统活跃度扫描");
            opportunities.resolveTodos(id,now);
        }
        if(!"ACTIVE".equals(next)) {
            String type="STAGNANT".equals(next)?"OPP_STAGNANT":"OPP_CLOSE_PENDING";
            String occurrence=baseline.toString();
            Set<Long> recipients=new LinkedHashSet<>(opportunities.reviewers(o.getTeamId()));
            if("STAGNANT".equals(next)) recipients.add(o.getOwnerId());
            for(Long recipient:recipients) opportunities.todo(type,id,occurrence,now.toLocalDate(),recipient,
                    "商机"+id+("STAGNANT".equals(next)?"停滞提醒":"待关闭裁决"),runId);
            if(pending!=null && !now.isBefore(pending.plusDays(3))) {
                for(Long recipient:opportunities.reviewers(o.getTeamId())) opportunities.todo("OPP_ESCALATED",id,occurrence,now.toLocalDate(),recipient,"商机"+id+"待关闭已超3天，请裁决；仍保持回收保护",runId);
            }
        }
        return changed;
    }

}
