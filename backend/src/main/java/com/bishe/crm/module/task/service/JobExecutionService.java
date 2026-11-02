/* 文件作用：串行执行扫描批次，逐条隔离失败，并记录失败项专用补跑。 */
package com.bishe.crm.module.task.service;

import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.task.entity.JobRun;
import com.bishe.crm.module.task.mapper.TaskMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 面向当前单实例部署；数据库批次唯一键另外保护尝试编号。 */
@Service
@Slf4j
public class JobExecutionService {
    private final TaskMapper mapper;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate transaction;

    /** 日志事务独立提交，不包住整个业务批次。 */
    public JobExecutionService(TaskMapper mapper, ObjectMapper json, Clock clock, PlatformTransactionManager manager) {
        this.mapper=mapper; this.json=json; this.clock=clock;
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 正常扫描或重试最新失败记录；客户端不能指定任意失败主键。 */
    public synchronized Map<String,Object> execute(String name, LocalDate date, Long actor, Long retryId,
        Supplier<List<Long>> candidates, Function<Long,Boolean> handle) {
        if (!Set.of("opportunity-activity-scan","payment-overdue-scan","customer-recycle-scan","business-reminder-scan").contains(name)
            || date.isAfter(LocalDate.now(clock))) throw new BizException(ErrorCode.BAD_REQUEST);
        String logical=name+":"+date;
        JobRun latest=mapper.latest(logical);
        List<Long> retryIds=null;
        if (retryId!=null) {
            JobRun source=mapper.job(retryId);
            if (source==null) throw new BizException(ErrorCode.NOT_FOUND);
            if (latest==null || !retryId.equals(latest.getId()) || !logical.equals(source.getLogicalRunId())
                || !Set.of("FAILED","PARTIAL_FAILURE").contains(source.getRunStatus())) throw new BizException(ErrorCode.CONFLICT);
            try { retryIds=json.readValue(source.getFailedBizIdsJson(), new TypeReference<List<Long>>() {}); }
            catch (Exception e) { throw new BizException(ErrorCode.CONFLICT); }
            if (retryIds==null || retryIds.isEmpty() || retryIds.stream().anyMatch(Objects::isNull)) throw new BizException(ErrorCode.CONFLICT);
        }
        JobRun run=new JobRun(); run.setJobName(name);run.setBusinessDate(date);run.setLogicalRunId(logical);
        run.setAttemptNo(latest==null?1:latest.getAttemptNo()+1);run.setStartedAt(LocalDateTime.now(clock));run.setTriggeredBy(actor);
        transaction.executeWithoutResult(status->{
            mapper.start(run);
            if (actor!=null) mapper.audit("JOB",run.getId(),retryId==null?"JOB_SCAN_REQUESTED":"JOB_RETRY_REQUESTED",actor,null,
                LocalDateTime.now(clock),retryId==null?"手动扫描："+name:"补跑失败项，来源执行记录："+retryId);
        });
        int success=0,changed=0;
        List<Long> failed=new ArrayList<>();
        String error=null;
        try {
            List<Long> ids=retryIds==null?candidates.get():retryIds;
            for (Long id:new LinkedHashSet<>(ids)) {
                try { if (Boolean.TRUE.equals(handle.apply(id))) changed++; success++; }
                catch (RuntimeException e) { failed.add(id);log.error("扫描单项失败 job={} id={}",name,id,e); }
            }
        } catch (RuntimeException e) {
            error="候选查询失败，请检查应用日志并重新执行扫描";
            log.error("扫描候选查询失败 job={}",name,e);
        }
        run.setFinishedAt(LocalDateTime.now(clock)); run.setSuccessCount(success);run.setFailureCount(failed.size());
        run.setFailedBizIdsJson(failed.toString());
        run.setRunStatus(error!=null||!failed.isEmpty()&&success==0?"FAILED":failed.isEmpty()?"SUCCESS":"PARTIAL_FAILURE");
        run.setErrorSummary(error!=null?error:failed.isEmpty()?null:"部分业务处理失败，可对最新记录补跑失败项");
        transaction.executeWithoutResult(status->{if(mapper.finish(run)!=1) throw new BizException(ErrorCode.CONFLICT);});
        return Map.of("id",run.getId(),"runId",logical,"processed",success,"changed",changed,"failedIds",failed,"status",run.getRunStatus());
    }
}
