/* 文件作用：按北京时间扫描逾期回款期次并记录每次任务结果。 */
package com.bishe.crm.module.contract.service;

import com.bishe.crm.module.contract.mapper.PaymentMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每日先于提醒和公海回收执行的逾期扫描。 */
@Component
@RequiredArgsConstructor
public class PaymentOverdueJob {
    private final PaymentMapper mapper;
    private final PaymentService payments;
    private final Clock businessClock;
    private final com.bishe.crm.module.task.service.JobExecutionService jobs;

    /** 每日 01:20 Asia/Shanghai 扫描未收期次，早于后续提醒和公海流程。 */
    @Scheduled(cron = "0 20 1 * * *", zone = "Asia/Shanghai")
    public void scheduledScan() {
        scan(LocalDate.now(businessClock), null);
    }

    /** 保留原调度入口，统一执行记录和失败项补跑。 */
    public void scan(LocalDate date, Long actorId) { scanSelected(date,actorId,null); }

    /** 扫描当前有效逾期期次，补跑时逐条重新检查状态和归属。 */
    public Map<String,Object> scanSelected(LocalDate date,Long actorId,Long retryId) {
        String runId="payment-overdue-scan:"+date;
        return jobs.execute("payment-overdue-scan",date,actorId,retryId,
            () -> mapper.overduePeriods(date).stream().map(row -> number(row.get("period_id"))).toList(),
            periodId -> {
                Long contractId=mapper.periodContract(periodId);
                if(contractId==null) return false;
                payments.scanOverduePeriod(contractId,periodId,null,null,null,date,runId);
                return true;
            });
    }

    /** JDBC整型主键转换。 */
    private Long number(Object value) { return value instanceof Number n ? n.longValue() : null; }
}
