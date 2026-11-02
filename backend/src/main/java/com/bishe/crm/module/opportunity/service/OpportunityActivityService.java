/* 文件作用：按业务时钟扫描商机活跃度并生成去重提醒。 */
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

/** 按业务时钟扫描商机活跃度并生成去重提醒。 */
@Service
@Slf4j
public class OpportunityActivityService {

    private final OpportunityMapper opportunities;
    private final CustomerMapper customers;
    private final Clock businessClock;
    private final TransactionTemplate transaction;
    private final com.bishe.crm.module.task.service.JobExecutionService jobs;

    /** 注入扫描依赖，并为每条商机配置独立事务。 */
    public OpportunityActivityService(
        OpportunityMapper opportunities,
        CustomerMapper customers,
        Clock businessClock,
        PlatformTransactionManager manager,
        com.bishe.crm.module.task.service.JobExecutionService jobs
    ) {
        this.opportunities = opportunities;
        this.customers = customers;
        this.businessClock = businessClock;
        this.jobs = jobs;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 按上海时区的每日计划触发扫描。 */
    @Scheduled(cron = "0 15 1 * * *", zone = "Asia/Shanghai")
    public void scheduledScan() {
        scanNow(null);
    }

    /** 手动或定时扫描当天候选商机。 */
    public Map<String, Object> scanNow(Long actorId) {
        return scanSelected(java.time.LocalDate.now(businessClock),actorId,null);
    }

    /** 补跑只选择服务器记录的失败对象，每条重新核对最新事实。 */
    public Map<String, Object> scanSelected(java.time.LocalDate date, Long actorId, Long retryId) {
        LocalDateTime now=LocalDateTime.now(businessClock);
        String runId="opportunity-activity-scan:"+date;
        return jobs.execute("opportunity-activity-scan",date,actorId,retryId,
            opportunities::activityCandidates,
            id -> transaction.execute(status -> scanOne(id,now,date,runId,actorId)));
    }

    /** 重新核对商机状态，计算活跃度并去重生成提醒。 */
    private boolean scanOne(Long id, LocalDateTime now, java.time.LocalDate businessDate, String runId, Long actorId) {
        Opportunity found = opportunities.find(id);
        if (found == null || customers.lock(found.getCustomerId()) == null) return false;
        Opportunity o = opportunities.lock(id);
        if (
            o == null ||
            !List.of("NEW", "FOLLOWING", "QUOTED", "DEAL").contains(o.getStageCode()) ||
            opportunities.activeContracts(id) > 0
        ) return false;
        // lastEffectiveProgressAt 由阶段动作、有效跟进及打回重算统一维护。
        // 不能再取自动 FOLLOWING 的阶段时间，否则已打回的跟进仍会延长计时。
        LocalDateTime baseline = o.getLastEffectiveProgressAt() == null
            ? o.getStageChangedAt()
            : o.getLastEffectiveProgressAt();
        if (baseline == null) baseline = o.getCreatedAt();
        long age = Math.max(0, Duration.between(baseline, now).toDays());
        int threshold = o.getStagnationDays(),
            grace = o.getGraceDays();
        String next = age < threshold
            ? "ACTIVE"
            : age < threshold + grace
                ? "STAGNANT"
                : "CLOSE_PENDING";
        LocalDateTime pending = "CLOSE_PENDING".equals(next)
            ? (o.getClosePendingAt() == null ? now : o.getClosePendingAt())
            : null;
        boolean changed =
            !next.equals(o.getActivityStatus()) || !Objects.equals(pending, o.getClosePendingAt());
        if (changed) {
            if (
                opportunities.updateActivity(id, o.getVersion(), next, pending, now) != 1
            ) throw new BizException(ErrorCode.CONFLICT);
            opportunities.audit(
                id,
                "OPPORTUNITY_ACTIVITY_CHANGED",
                actorId,
                o.getTeamId(),
                now,
                "{\"activityStatus\":\"" + o.getActivityStatus() + "\"}",
                "{\"activityStatus\":\"" + next + "\"}",
                "系统活跃度扫描"
            );
            opportunities.resolveTodos(id, now);
        }
        if (!"ACTIVE".equals(next)) {
            String type = "STAGNANT".equals(next) ? "OPP_STAGNANT" : "OPP_CLOSE_PENDING";
            String occurrence = baseline.toString();
            Set<Long> recipients = new LinkedHashSet<>(opportunities.reviewers(o.getTeamId()));
            if ("STAGNANT".equals(next)) recipients.add(o.getOwnerId());
            for (Long recipient : recipients)
                opportunities.todo(
                    type,
                    id,
                    occurrence,
                    businessDate,
                    recipient,
                    "商机" + id + ("STAGNANT".equals(next) ? "停滞提醒" : "待关闭裁决"),
                    runId
                );
            if (pending != null && !now.isBefore(pending.plusDays(3))) {
                for (Long recipient : opportunities.reviewers(o.getTeamId()))
                    opportunities.todo(
                        "OPP_ESCALATED",
                        id,
                        occurrence,
                        businessDate,
                        recipient,
                        "商机" + id + "待关闭已超3天，请裁决；仍保持回收保护",
                        runId
                    );
            }
        }
        return changed;
    }
}
