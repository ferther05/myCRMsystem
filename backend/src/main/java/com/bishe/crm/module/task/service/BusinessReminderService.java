/* 文件作用：补齐下次跟进与合同停滞提醒，复用现有执行日志和失败补跑。 */
package com.bishe.crm.module.task.service;

import com.bishe.crm.module.customer.mapper.CustomerMapper;
import com.bishe.crm.module.task.mapper.BusinessReminderMapper;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class BusinessReminderService {
    private final BusinessReminderMapper mapper;
    private final CustomerMapper customers;
    private final JobExecutionService jobs;
    private final Clock clock;
    private final PlatformTransactionManager transactions;
    public static final int CONTRACT_STAGNANT_DAYS=30;

    /** 每十分钟检查一次；精确到达下一步约定时间才触发。 */
    @Scheduled(cron="0 5/10 * * * *",zone="Asia/Shanghai")
    public void scheduledScan() { scan(LocalDate.now(clock),null,null); }

    /** 按当前真实时钟判断；历史失败补跑不重放过去的业务状态。 */
    public Map<String,Object> scan(LocalDate date,Long actor,Long retry) {
        return jobs.execute("business-reminder-scan",date,actor,retry,mapper::candidates,
            id -> new TransactionTemplate(transactions).execute(status -> reconcile(id)));
    }

    /** 客户锁内重新读取计划和归属，逐客户事务可独立失败补跑。 */
    public boolean reconcile(Long id) {
        var c=customers.lock(id);
        LocalDateTime now=LocalDateTime.now(clock); LocalDate date=now.toLocalDate();
        boolean active=c!=null&&!Boolean.TRUE.equals(c.getDeleted())&&"PRIVATE".equals(c.getSeaType())&&c.getPermanentDealAt()==null;
        var due=active?mapper.dueFollows(id,now):List.<Map<String,Object>>of();
        var owners=active?mapper.recipients(c.getOwnerId(),c.getTeamId(),false):List.<Long>of();
        var keys=due.stream().map(f->"follow:"+f.get("id")).toList();
        mapper.resolve("FOLLOW_DUE","CUSTOMER",id,keys,owners,date,now);
        for(String key:keys) for(Long owner:owners)
            mapper.upsert("FOLLOW_DUE","CUSTOMER",id,key,date,owner,"约定的下次跟进时间已到，请补充跟进记录","business-reminder-scan:"+date);
        var birthdayKeys=active?mapper.birthdays(id,date).stream().map(contact->"birthday:"+contact).toList():List.<String>of();
        mapper.resolve("CARE_BIRTHDAY","CUSTOMER",id,birthdayKeys,owners,date,now);
        for(String key:birthdayKeys) for(Long owner:owners)
            mapper.upsert("CARE_BIRTHDAY","CUSTOMER",id,key,date,owner,"客户联系人生日关怀，请查看联系人资料","business-reminder-scan:"+date);
        var stagnant=active?mapper.stagnantContracts(id,now.minusDays(CONTRACT_STAGNANT_DAYS)):List.<Long>of();
        var receivers=active?mapper.recipients(c.getOwnerId(),c.getTeamId(),true):List.<Long>of();
        var reviews=active?mapper.reviews(id):List.<Map<String,Object>>of();
        for(Long contract:mapper.contracts(id)) {
            var ck=stagnant.contains(contract)?List.of("contract:"+contract):List.<String>of();
            mapper.resolve("CONTRACT_STAGNANT","CONTRACT",contract,ck,receivers,date,now);
            for(String key:ck) for(Long user:receivers)
                mapper.upsert("CONTRACT_STAGNANT","CONTRACT",contract,key,date,user,"合同办理连续30天无进展，请核对登记或签署材料","business-reminder-scan:"+date);
            var review=reviews.stream().filter(r->((Number)r.get("id")).longValue()==contract).findFirst();
            List<Long> reviewers=List.of();List<String> reviewKeys=List.of();
            if(review.isPresent()) {
                var row=review.get();Long owner=((Number)row.get("owner_id")).longValue();
                reviewers=mapper.reviewers(((Number)row.get("team_id")).longValue(),owner);
                if(reviewers.isEmpty())reviewers=mapper.administrators(owner);
                reviewKeys=List.of(row.get("signing_progress")+":"+contract);
            }
            mapper.resolve("CONTRACT_REVIEW","CONTRACT",contract,reviewKeys,reviewers,date,now);
            for(String key:reviewKeys)for(Long user:reviewers)
                mapper.upsert("CONTRACT_REVIEW","CONTRACT",contract,key,date,user,key.startsWith("DRAFT_REVIEW")?"合同登记已提交，请核对或退回补充":"实际签署文件已提交，请核验最新版本","business-reminder-scan:"+date);
        }
        return !due.isEmpty()||!stagnant.isEmpty()||!birthdayKeys.isEmpty();
    }
}
