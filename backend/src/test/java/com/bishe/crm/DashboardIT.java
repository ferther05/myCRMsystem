/* 文件作用：隔离数据验证真实看板金额、日期、历史归属、权限和Redis缓存一致性。 */
package com.bishe.crm;

import static org.assertj.core.api.Assertions.*;
import com.bishe.crm.module.analytics.service.DashboardCache;
import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.contract.mapper.*;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @ActiveProfiles("local")
class DashboardIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired SystemUserMapper users;
    @Autowired JwtTokenService tokens;
    @Autowired Clock clock;
    @Autowired DashboardCache cache;
    @Autowired ContractMapper contracts;
    @Autowired PaymentMapper payments;
    @Autowired PlatformTransactionManager manager;
    PaymentFlowIT f;
    LocalDate today;
    Long period,receipt;
    /** 每次建立独立团队与合同，不将演示数据写成测试预期。 */
    @BeforeEach void setup() {
        f=new PaymentFlowIT();f.http=http;f.jdbc=jdbc;f.tokens=tokens;f.users=users;f.businessClock=clock;f.setup();today=LocalDate.now(clock);
        jdbc.update("UPDATE crm_contract SET performance_owner_id=?,performance_team_id=?,performance_confirmed_at=?,performance_amount=1000,outstanding_amount=750,received_amount=250 WHERE id=?",f.sales,f.team,today.atTime(1,0),f.contract);
        jdbc.update("UPDATE crm_opportunity SET created_at=? WHERE id=?",today.atStartOfDay(),f.opportunity);
        jdbc.update("INSERT INTO crm_opportunity_stage_log(opportunity_id,to_stage,action_code,occurred_at) VALUES(?,'DEAL','AUTO_ADVANCE',?)",f.opportunity,today.atTime(0,1));
        jdbc.update("INSERT INTO crm_opportunity_stage_log(opportunity_id,from_stage,to_stage,action_code,occurred_at) VALUES(?,'DEAL','WON','AUTO_ADVANCE',?)",f.opportunity,today.atTime(1,0));
        jdbc.update("INSERT INTO crm_payment_plan(contract_id,version_no,total_planned_amount,approval_status,is_current,created_by) VALUES(?,1,1000,'ACTIVE',1,?)",f.contract,f.sales);
        long plan=jdbc.queryForObject("SELECT id FROM crm_payment_plan WHERE contract_id=?",Long.class,f.contract);
        jdbc.update("UPDATE crm_contract SET current_plan_id=? WHERE id=?",plan,f.contract);
        jdbc.update("INSERT INTO crm_payment_period(plan_id,period_no,planned_amount,received_amount,due_date,period_status) VALUES(?,1,1000,250,?,'PARTIAL')",plan,today.minusDays(1));
        period=jdbc.queryForObject("SELECT id FROM crm_payment_period WHERE plan_id=?",Long.class,plan);
        jdbc.update("INSERT INTO crm_payment_record(contract_id,period_id,received_amount,received_at,registered_by,idempotency_key,performance_owner_id,performance_team_id) VALUES(?,?,250,?,?,?,?,?)",f.contract,period,today.atTime(2,0),f.sales,f.prefix,f.sales,f.team);
        receipt=jdbc.queryForObject("SELECT id FROM crm_payment_record WHERE contract_id=?",Long.class,f.contract);cache.invalidate();
    }
    /** 测试数据按ID清理，清除缓存代次，保留真实业务。 */
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM crm_opportunity_stage_log WHERE opportunity_id=?",f.opportunity);
        f.cleanup();cache.invalidate();
    }
    /** 同一合同多个计划和期次不会把合同金额乘倍，余额与逾期使用当前计划。 */
    @Test void sumsDoNotMultiplyAndMonthZerosAreFilled() {
        var d=data(f.sales,"&refresh=true");assertThat(d.path("metrics").path("signedAmount").decimalValue()).isEqualByComparingTo("1000");
        assertThat(d.path("metrics").path("receivedAmount").decimalValue()).isEqualByComparingTo("250");
        assertThat(d.path("metrics").path("outstandingAmount").decimalValue()).isEqualByComparingTo("750");
        assertThat(d.path("metrics").path("overdueAmount").decimalValue()).isEqualByComparingTo("750");
        assertThat(d.path("metrics").path("currentCollectionRate").decimalValue()).isEqualByComparingTo("25");
        var year=ok(call(f.sales,"/analytics/overview?from="+today.withDayOfYear(1)+"&to="+today+"&refresh=true"));
        assertThat(year.path("trends").size()).isEqualTo(today.getMonthValue());
        assertThat(year.path("trends").get(0).path("receivedAmount").decimalValue()).isEqualByComparingTo(today.getMonthValue()==1?"250":"0");
        jdbc.update("INSERT INTO crm_payment_plan(contract_id,version_no,total_planned_amount,approval_status,is_current,created_by) VALUES(?,2,1000,'DRAFT',0,?)",f.contract,f.sales);
        assertThat(data(f.sales,"&refresh=true").path("metrics").path("signedAmount").decimalValue()).isEqualByComparingTo("1000");
    }
    /** 本人、团队、全局和负责人筛选均经真实接口校验。 */
    @Test void scopesAndOwnerFiltersCannotLeak() {
        assertThat(data(f.peer,"").path("metrics").path("receivedAmount").decimalValue()).isZero();
        assertThat(data(f.manager,"").path("metrics").path("receivedAmount").decimalValue()).isEqualByComparingTo("250");
        assertThat(call(f.peer,path()+"&ownerId="+f.sales).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(data(f.admin,"&ownerId="+f.sales).path("metrics").path("receivedAmount").decimalValue()).isEqualByComparingTo("250");
        assertThat(ok(call(f.sales,"/analytics/owners")).size()).isEqualTo(1);
        Long original=jdbc.queryForObject("SELECT team_id FROM sys_user WHERE id=?",Long.class,f.peer);
        try {jdbc.update("UPDATE sys_user SET team_id=NULL WHERE id=?",f.peer);assertThat(call(f.manager,path()+"&ownerId="+f.peer).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);} finally {jdbc.update("UPDATE sys_user SET team_id=? WHERE id=?",original,f.peer);}
    }
    /** 后续交接改变当前存量，但不搬走签约/到账历史业绩，也不授权旧负责人查看客户。 */
    @Test void historicalAttributionSurvivesTransfer() {
        jdbc.update("UPDATE crm_customer SET owner_id=? WHERE id=?",f.peer,f.customer);
        jdbc.update("UPDATE crm_contract SET owner_id=? WHERE id=?",f.peer,f.contract);cache.invalidate();
        var old=data(f.sales,"&refresh=true");assertThat(old.path("metrics").path("customerCount").asLong()).isZero();
        assertThat(old.path("metrics").path("signedAmount").decimalValue()).isEqualByComparingTo("1000");
        assertThat(old.path("metrics").path("receivedAmount").decimalValue()).isEqualByComparingTo("250");
        assertThat(data(f.peer,"").path("metrics").path("signedAmount").decimalValue()).isZero();
        assertThat(call(f.sales,"/contracts/"+f.contract).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
    /** 日期首尾闭区间转半开时间区间；当前余额不随期间变化。 */
    @Test void dateBoundsAndVoidsAreCorrect() {
        jdbc.update("UPDATE crm_payment_record SET received_at=? WHERE id=?",today.plusDays(1).atStartOfDay(),receipt);
        assertThat(data(f.sales,"&refresh=true").path("metrics").path("receivedAmount").decimalValue()).isZero();
        jdbc.update("UPDATE crm_payment_record SET received_at=? WHERE id=?",today.atTime(23,59,59,999000000),receipt);
        assertThat(data(f.sales,"&refresh=true").path("metrics").path("receivedAmount").decimalValue()).isEqualByComparingTo("250");
        new TransactionTemplate(manager).executeWithoutResult(tx->payments.voidReceipt(receipt,f.contract,f.admin,LocalDateTime.now(clock),"隔离测试作废",0L));
        assertThat(data(f.sales,"").path("metrics").path("receivedAmount").decimalValue()).isZero();
        jdbc.update("UPDATE crm_payment_period SET due_date=? WHERE id=?",today,period);
        assertThat(data(f.sales,"&refresh=true").path("metrics").path("overdueAmount").decimalValue()).isZero();
    }
    /** 转化率来自同批次阶段历史，重复回退日志不重复累计；零分母为null。 */
    @Test void historicalConversionUsesDistinctOpportunity() {
        var c=data(f.sales,"").path("conversion");assertThat(c.path("cohortCount").asInt()).isEqualTo(1);assertThat(c.path("dealToWonRate").decimalValue()).isEqualByComparingTo("100");
        jdbc.update("INSERT INTO crm_opportunity_stage_log(opportunity_id,to_stage,action_code,occurred_at) VALUES(?,'WON','AUTO_ADVANCE',?)",f.opportunity,today.atTime(3,0));
        assertThat(data(f.sales,"&refresh=true").path("conversion").path("wonCount").asInt()).isEqualTo(1);
        assertThat(data(f.peer,"").path("conversion").path("dealToWonRate").isNull()).isTrue();
    }
    /** 过程质量按当前阶段及所选期间统计，并保持本人/团队/全局范围。 */
    @Test void processQualityUsesScopedCurrentStageAndPeriodRollbacks() {
        jdbc.update("UPDATE crm_opportunity SET stage_code='FOLLOWING',created_at=? WHERE id=?",today.minusDays(4).atTime(0,0),f.opportunity);
        jdbc.update("DELETE FROM crm_opportunity_stage_log WHERE opportunity_id=?",f.opportunity);
        LocalDateTime enteredFollowing=today.atTime(2,0);
        jdbc.update("INSERT INTO crm_opportunity_stage_log(opportunity_id,from_stage,to_stage,action_code,occurred_at) VALUES(?,'NEW','FOLLOWING','AUTO_ADVANCE',?)",f.opportunity,enteredFollowing);
        jdbc.update("INSERT INTO crm_opportunity_stage_log(opportunity_id,from_stage,to_stage,action_code,occurred_at) VALUES(?,'FOLLOWING','NEW','ROLLBACK',?)",f.opportunity,today.atTime(1,0));
        var specialist=data(f.sales,"&refresh=true").path("processQuality");
        assertThat(specialist.path("rollbackCount").asLong()).isEqualTo(1);
        var duration=specialist.path("stageDurations").get(1);
        assertThat(duration.path("code").asText()).isEqualTo("FOLLOWING");
        assertThat(duration.path("opportunityCount").asLong()).isEqualTo(1);
        BigDecimal expectedDays=BigDecimal.valueOf(Duration.between(enteredFollowing,LocalDateTime.now(clock)).toMillis()/86_400_000.0).setScale(2,RoundingMode.HALF_UP);
        assertThat(duration.path("averageDays").decimalValue()).isEqualByComparingTo(expectedDays);
        assertThat(data(f.peer,"&refresh=true").path("processQuality").path("rollbackCount").asLong()).isZero();
        assertThat(data(f.manager,"&refresh=true").path("processQuality").path("rollbackCount").asLong()).isEqualTo(1);
        assertThat(data(f.admin,"&ownerId="+f.sales+"&refresh=true").path("processQuality").path("rollbackCount").asLong()).isEqualTo(1);
        assertThat(data(f.sales,"&refresh=true").path("processQuality").path("stageDurations").get(0).path("opportunityCount").asLong()).isZero();
        assertThat(data(f.sales,"&refresh=true").path("processQuality").path("stageDurations").get(1).path("opportunityCount").asLong()).isEqualTo(1);
        jdbc.update("UPDATE crm_opportunity_stage_log SET occurred_at=? WHERE opportunity_id=? AND action_code='ROLLBACK'",today.minusDays(1).atTime(23,59,59),f.opportunity);
        assertThat(data(f.sales,"&refresh=true").path("processQuality").path("rollbackCount").asLong()).isZero();
        jdbc.update("DELETE FROM crm_opportunity_stage_log WHERE opportunity_id=?",f.opportunity);
        var fallback=data(f.sales,"&refresh=true").path("processQuality").path("stageDurations").get(1);
        assertThat(fallback.path("opportunityCount").asLong()).isEqualTo(1);
        assertThat(fallback.path("averageDays").decimalValue()).isBetween(new BigDecimal("4.00"),new BigDecimal("5.00"));
    }
    /** Redis真实读写、不同用户隔离、提交后失效、回滚保持原缓存均可复现。 */
    @Test void cacheInvalidatesOnlyAfterCommit() {
        assertThat(data(f.sales,"").path("source").asText()).isEqualTo("数据库");
        assertThat(data(f.sales,"").path("source").asText()).isEqualTo("Redis缓存");
        assertThat(data(f.peer,"").path("source").asText()).isEqualTo("数据库");
        jdbc.update("UPDATE crm_contract SET performance_confirmed_at=NULL WHERE id=?",f.contract);
        new TransactionTemplate(manager).executeWithoutResult(tx->{contracts.capturePerformance(f.contract,today.atTime(1,0));tx.setRollbackOnly();});
        assertThat(data(f.sales,"").path("source").asText()).isEqualTo("Redis缓存");
        new TransactionTemplate(manager).executeWithoutResult(tx->contracts.capturePerformance(f.contract,today.atTime(1,0)));
        assertThat(data(f.sales,"").path("source").asText()).isEqualTo("数据库");
        assertThat(data(f.sales,"&refresh=true").path("source").asText()).isEqualTo("数据库");
    }
    /** 待办始终实时，且只显示本人，不受聚合缓存影响。 */
    @Test void actionsAreLiveAndPersonal() {
        data(f.sales,"");
        jdbc.update("INSERT INTO crm_todo(todo_type,biz_type,biz_id,occurrence_key,remind_date,recipient_id,title) VALUES('CUSTOMER_WARNING','CUSTOMER',?,'dashboard-it',?,?,'隔离测试提醒')",f.customer,today,f.sales);
        try {assertThat(data(f.sales,"").path("actions").get(0).path("count").asInt()).isEqualTo(1);assertThat(data(f.peer,"").path("actions").size()).isZero();}finally {jdbc.update("DELETE FROM crm_todo WHERE biz_type='CUSTOMER' AND biz_id=?",f.customer);}
    }
    /** 参数错误不返回全量结果，匿名和首次改密仍拒绝。 */
    @Test void validatesDatesAndAuthentication() {
        assertThat(call(f.sales,"/analytics/overview?from=2026-11-02&to=2026-11-01").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(f.sales,"/analytics/overview?from=2000-01-01&to="+today).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(f.sales,"/analytics/overview?from=bad").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.getForEntity("/api/analytics/overview",JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    /** 实际到账保留负责人快照，同时沿用仅负责人登记、管理员不可代录的权限。 */
    @Test void receiptSnapshotKeepsExistingWritePermissions() {
        String key=UUID.randomUUID().toString();
        assertThat(f.call("POST","/payment-records",f.admin,f.receipt(period,50,key)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ok(f.call("POST","/payment-records",f.sales,f.receipt(period,50,key)));
        assertThat(jdbc.queryForObject("SELECT performance_owner_id FROM crm_payment_record WHERE idempotency_key=?",Long.class,key)).isEqualTo(f.sales);
        assertThat(jdbc.queryForObject("SELECT registered_by FROM crm_payment_record WHERE idempotency_key=?",Long.class,key)).isEqualTo(f.sales);
        assertThat(data(f.sales,"").path("metrics").path("receivedAmount").decimalValue()).isEqualByComparingTo("300");
    }
    /** 首次确认快照不可被再次确认覆盖；终止仍保留历史，终止余额不计当前应收。 */
    @Test void confirmationSnapshotIsImmutableAndTerminationRetainsHistory() {
        new TransactionTemplate(manager).executeWithoutResult(tx->{assertThat(contracts.capturePerformance(f.contract,today.atTime(3,0))).isZero();});
        jdbc.update("UPDATE crm_contract SET contract_status='TERMINATED',outstanding_amount=0 WHERE id=?",f.contract);
        var d=data(f.sales,"&refresh=true");
        assertThat(d.path("metrics").path("signedAmount").decimalValue()).isEqualByComparingTo("1000");
        assertThat(d.path("metrics").path("currentContractAmount").decimalValue()).isZero();
        assertThat(d.path("metrics").path("currentCollectionRate").isNull()).isTrue();
    }
    /** 固定本日筛选，追加测试参数。 */
    String path() {return "/analytics/overview?from="+today+"&to="+today;}
    /** 返回成功data。 */
    JsonNode data(Long actor,String extra) {return ok(call(actor,path()+extra));}
    /** 真实JWT请求。 */
    ResponseEntity<JsonNode> call(Long actor,String path) {var h=new HttpHeaders();h.setBearerAuth(tokens.issue(AuthPrincipal.from(users.findWithTeamById(actor))).value());return http.exchange("/api"+path,HttpMethod.GET,new HttpEntity<>(h),JsonNode.class);}
    /** 检查状态与业务码，便于定位SQL失败。 */
    JsonNode ok(ResponseEntity<JsonNode> r) {assertThat(r.getStatusCode()).withFailMessage("%s",r.getBody()).isEqualTo(HttpStatus.OK);assertThat(r.getBody().path("code").asInt()).isZero();return r.getBody().path("data");}
}
