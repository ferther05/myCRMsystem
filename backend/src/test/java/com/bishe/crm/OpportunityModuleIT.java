package com.bishe.crm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.opportunity.mapper.OpportunityMapper;
import com.bishe.crm.module.opportunity.service.OpportunityActivityService;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class OpportunityModuleIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    @Autowired SystemUserMapper users;
    @Autowired PasswordEncoder passwords;
    @Autowired OpportunityActivityService activity;
    @MockitoSpyBean OpportunityMapper opportunities;
    @MockitoSpyBean Clock businessClock;
    String prefix;
    Long east,south,admin,manager,sales,peer,third,fourth,foreign,customer;
    List<Long> actors=new ArrayList<>(),customers=new ArrayList<>();
    LocalDateTime now=LocalDateTime.of(2026,10,21,12,0);

    @BeforeEach void setup() {
        prefix="op-"+UUID.randomUUID().toString().substring(0,8);
        doReturn(now.atZone(ZoneId.of("Asia/Shanghai")).toInstant()).when(businessClock).instant();
        east=team("east");south=team("south");
        admin=user("admin","ADMIN",null);manager=user("manager","MANAGER",east);
        sales=user("sales","SPECIALIST",east);peer=user("peer","SPECIALIST",east);
        third=user("third","SPECIALIST",east);fourth=user("fourth","SPECIALIST",east);foreign=user("foreign","SPECIALIST",south);
        customer=customer(sales,east,"FOLLOWING","PRIVATE");
    }
    @AfterEach void cleanup() {
        reset(opportunities,businessClock);
        for(Long c:customers) {
            for(Long o:jdbc.queryForList("SELECT id FROM crm_opportunity WHERE customer_id=?",Long.class,c)) {
                jdbc.update("DELETE FROM crm_todo WHERE biz_type='OPPORTUNITY' AND biz_id=?",o);
                jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='OPPORTUNITY' AND biz_id=?",o);
                jdbc.update("DELETE FROM crm_follow_record WHERE opportunity_id=?",o);
                jdbc.update("DELETE FROM crm_contract WHERE opportunity_id=?",o);
                jdbc.update("DELETE FROM crm_opportunity_collaborator WHERE opportunity_id=?",o);
                jdbc.update("DELETE FROM crm_opportunity_stage_log WHERE opportunity_id=?",o);
                jdbc.update("DELETE FROM crm_opportunity WHERE id=?",o);
            }
            jdbc.update("DELETE FROM crm_follow_record WHERE customer_id=?",c);
            jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=?",c);
            jdbc.update("DELETE FROM crm_contact WHERE customer_id=?",c);
            jdbc.update("DELETE FROM crm_customer WHERE id=?",c);
        }
        for(Long a:actors) {
            jdbc.update("DELETE FROM crm_job_log WHERE triggered_by=?",a);
            jdbc.update("DELETE FROM crm_operation_log WHERE operator_id=?",a);
            jdbc.update("DELETE FROM sys_user WHERE id=?",a);
        }
        jdbc.update("DELETE FROM sys_team WHERE id IN (?,?)",east,south);
    }

    @Test void creationInheritsCustomerOwnerAndEnforcesPrivateScopeAndRequiredFields() {
        long id=create(admin,customer,"第一条",100);
        JsonNode d=detail(sales,id);
        assertThat(d.path("ownerId").asLong()).isEqualTo(sales);
        assertThat(d.path("stageCode").asText()).isEqualTo("NEW");
        assertThat(d.path("graceDays").asInt()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT customer_status FROM crm_customer WHERE id=?",String.class,customer)).isEqualTo("PROTECTED");
        status(call("POST","/api/opportunities",foreign,payload(customer,"越权",1)),403);
        long pub=customer(null,null,"NEW","PUBLIC");
        status(call("POST","/api/opportunities",admin,payload(pub,"公海",1)),403);
        long fresh=customer(sales,east,"NEW","PRIVATE");
        status(call("POST","/api/opportunities",admin,payload(fresh,"未首联",1)),400);
        var missing=payload(customer,"缺日期",1);missing.remove("expectedCloseDate");
        status(call("POST","/api/opportunities",admin,missing),400);
        var negative=payload(customer,"负金额",1);negative.put("expectedAmount",-1);
        status(call("POST","/api/opportunities",admin,negative),400);
    }

    @Test void transitionsRequireEvidenceAdjacentStepsAndReasonsAndKeepAudit() {
        long id=create(admin,customer,"阶段",100);
        status(stage(sales,id,"FOLLOWING","专员直接推进"),403);
        status(stage(manager,id,"FOLLOWING","经理直接推进"),403);
        status(stage(admin,id,"QUOTED","越级"),400);
        status(stage(admin,id,"FOLLOWING","缺有效跟进"),400);
        status(call("POST","/api/opportunities/"+id+"/evidence",sales,Map.of("version",version(id),"kind","QUOTE","quoteAmount",200,"description","不能绕过有效跟进")),400);
        follow(sales,id,"NOT_CONNECTED");
        assertThat(detail(sales,id).path("stageCode").asText()).isEqualTo("NEW");
        follow(sales,id,"NEED_CONFIRMED");
        assertThat(detail(sales,id).path("stageCode").asText()).isEqualTo("FOLLOWING");
        status(stage(admin,id,"NEW",null),400);
        status(stage(admin,id,"QUOTED","无报价"),400);
        evidence(sales,id,"QUOTE",200,"发送正式报价方案");
        assertThat(detail(sales,id).path("stageCode").asText()).isEqualTo("QUOTED");
        assertThat(detail(sales,id).path("graceDays").asInt()).isEqualTo(15);
        status(stage(admin,id,"DEAL","无客户确认"),400);
        evidence(sales,id,"CONFIRMATION",0,"客户电话明确确认合作");
        assertThat(detail(sales,id).path("stageCode").asText()).isEqualTo("DEAL");
        status(stage(admin,id,"WON","无签署资料"),400);
        assertThat(detail(sales,id).path("stageHistory").get(0).path("operatorName").asText()).isEqualTo("系统自动");
        assertThat(detail(sales,id).path("stageHistory").get(0).path("operatorId").isNull()).isTrue();
        ok(stage(admin,id,"QUOTED","重新核对实施范围"));
        assertThat(detail(sales,id).path("stageHistory").size()).isEqualTo(5);
        assertThat(detail(sales,id).path("operationHistory").size()).isGreaterThanOrEqualTo(7);
    }

    @Test void terminalStateCannotReopenAndClosingIsAdminOnly() {
        long id=create(admin,customer,"关闭",100);
        status(stage(sales,id,"CLOSED","长期无推进"),403);
        status(stage(manager,id,"CLOSED","经理不能关闭"),403);
        status(stage(admin,id,"CLOSED",null),400);
        ok(stage(admin,id,"CLOSED","管理员确认终止"));
        status(stage(admin,id,"NEW","恢复"),400);
        status(call("PUT","/api/opportunities/"+id,sales,Map.of("version",version(id),"opportunityName","修改终态","expectedAmount",9,"expectedCloseDate","2026-12-01")),400);
        assertThat(jdbc.queryForObject("SELECT close_reason FROM crm_opportunity WHERE id=?",String.class,id)).isEqualTo("管理员确认终止");
        assertThat(detail(manager,id).path("activityStatus").asText()).isEqualTo("TERMINATED");
        assertThat(jdbc.queryForObject("SELECT customer_status FROM crm_customer WHERE id=?",String.class,customer)).isEqualTo("NEW");
    }

    @Test void contractBlocksRollbackAndLosing() {
        long id=create(admin,customer,"合同保护",100);follow(sales,id,"NEED_CONFIRMED");
        jdbc.update("INSERT INTO crm_contract(contract_no,customer_id,opportunity_id,owner_id,team_id,contract_name,contract_amount,contract_status,created_by) VALUES(?,?,?,?,?,? ,100,'SIGNED',?)",prefix,customer,id,sales,east,prefix,sales);
        status(stage(admin,id,"NEW","有合同回退"),400);
        status(stage(admin,id,"LOST","已有合同"),400);
    }

    @Test void closingOneOpportunityDoesNotEraseOtherProtectionOrFinalCustomerStates() {
        long first=create(admin,customer,"保护一",100),second=create(admin,customer,"保护二",100);
        ok(stage(admin,first,"CLOSED","关闭其中一条"));
        assertThat(jdbc.queryForObject("SELECT customer_status FROM crm_customer WHERE id=?",String.class,customer)).isEqualTo("PROTECTED");
        jdbc.update("UPDATE crm_customer SET customer_status='DEAL_PENDING' WHERE id=?",customer);
        ok(stage(admin,second,"CLOSED","独立商机关闭不得覆盖合同保护状态"));
        assertThat(jdbc.queryForObject("SELECT customer_status FROM crm_customer WHERE id=?",String.class,customer)).isEqualTo("DEAL_PENDING");
        jdbc.update("UPDATE crm_customer SET customer_status='DEAL' WHERE id=?",customer);
        opportunities.refreshCustomerProtection(customer,now);
        assertThat(jdbc.queryForObject("SELECT customer_status FROM crm_customer WHERE id=?",String.class,customer)).isEqualTo("DEAL");
    }

    @Test void thresholdsOnlyAffectOneOpportunityAndEditingMoneyDoesNotRefreshProgress() {
        long first=create(admin,customer,"阈值1",100),second=create(admin,customer,"阈值2",200);
        var baseline=jdbc.queryForMap("SELECT stage_changed_at,last_effective_progress_at FROM crm_opportunity WHERE id=?",first);
        status(call("PATCH","/api/opportunities/"+first+"/threshold",sales,Map.of("version",0,"stagnationDays",9,"graceDays",3)),403);
        ok(call("PATCH","/api/opportunities/"+first+"/threshold",manager,Map.of("version",0,"stagnationDays",9,"graceDays",3)));
        follow(sales,first,"NEED_CONFIRMED");
        assertThat(detail(sales,first).path("stagnationDays").asInt()).isEqualTo(9);
        assertThat(detail(sales,second).path("stagnationDays").asInt()).isEqualTo(20);
        ok(call("PUT","/api/opportunities/"+first,sales,Map.of("version",version(first),"opportunityName","更新金额","expectedAmount",1234.56,"expectedCloseDate","2026-12-12")));
        assertThat(jdbc.queryForMap("SELECT stage_changed_at,last_effective_progress_at FROM crm_opportunity WHERE id=?",first)).isEqualTo(baseline);
    }

    @Test void collaborationHasNarrowReadsAndWriteRestrictionsAndVersionChecks() {
        long id=create(admin,customer,"协作",100);
        addMember(sales,id,peer,10);
        var d=detail(peer,id);
        assertThat(d.path("canEdit").asBoolean()).isFalse();
        assertThat(d.path("contacts").size()).isEqualTo(1);
        assertThat(d.path("operationHistory").size()).isZero();
        status(call("GET","/api/customers/"+customer,peer,null),404);
        status(stage(peer,id,"FOLLOWING",null),403);
        status(call("GET","/api/opportunities/"+id+"/members",peer,null),403);
        long member=d.path("collaborators").get(0).path("id").asLong();
        status(call("DELETE","/api/opportunities/"+id+"/collaborators/"+member+"?version="+version(id),peer,null),403);
        status(call("POST","/api/opportunities/"+id+"/collaborators",sales,Map.of("version",0,"userId",third,"splitRatio",10)),409);
        addMember(sales,id,third,10);addMember(sales,id,fourth,10);
        status(call("POST","/api/opportunities/"+id+"/collaborators",sales,Map.of("version",version(id),"userId",manager,"splitRatio",1)),400);
        ok(call("DELETE","/api/opportunities/"+id+"/collaborators/"+member+"?version="+version(id),sales,null));
        status(call("GET","/api/opportunities/"+id,peer,null),404);
    }

    @Test void collaborationRatioAndTeamBoundariesCannotBeBypassed() {
        long id=create(admin,customer,"比例",100); addMember(sales,id,peer,25);
        status(call("POST","/api/opportunities/"+id+"/collaborators",sales,Map.of("version",version(id),"userId",third,"splitRatio",6)),400);
        status(call("POST","/api/opportunities/"+id+"/collaborators",sales,Map.of("version",version(id),"userId",foreign,"splitRatio",1)),403);
        status(call("POST","/api/opportunities/"+id+"/collaborators",sales,Map.of("version",version(id),"userId",manager,"splitRatio",1)),403);
    }

    @Test void linkedFollowOnlyRefreshesCurrentOpportunityAndReturnRecalculatesClock() {
        long id=create(admin,customer,"跟进1",100),other=create(admin,customer,"跟进2",200);addMember(sales,id,peer,10);
        var baseline=jdbc.queryForObject("SELECT last_effective_progress_at FROM crm_opportunity WHERE id=?",LocalDateTime.class,other);
        now=now.plusDays(1);doReturn(now.atZone(ZoneId.of("Asia/Shanghai")).toInstant()).when(businessClock).instant();
        jdbc.update("UPDATE crm_customer SET version=256 WHERE id=?",customer);
        var d=detail(peer,id);
        var body=new HashMap<String,Object>(Map.of("customerVersion",d.path("customerVersion").asLong(),"contactId",d.path("contacts").get(0).path("id").asLong(),"followMethod","PHONE","content","已核对客户项目范围和计划","resultCode","NEED_CONFIRMED","substantiveResult","客户预算和采购时间已确认","nextAction","发送方案","nextActionAt",now.plusDays(1).toString(),"followedAt",now.minusMinutes(1).toString()));
        long follow=ok(call("POST","/api/opportunities/"+id+"/follow-records",peer,body)).asLong();
        assertThat(detail(sales,id).path("stageCode").asText()).isEqualTo("FOLLOWING");
        assertThat(detail(sales,other).path("stageCode").asText()).isEqualTo("NEW");
        assertThat(jdbc.queryForObject("SELECT last_effective_progress_at FROM crm_opportunity WHERE id=?",LocalDateTime.class,id)).isEqualTo(now.minusMinutes(1));
        assertThat(jdbc.queryForObject("SELECT last_effective_progress_at FROM crm_opportunity WHERE id=?",LocalDateTime.class,other)).isEqualTo(baseline);
        long cv=jdbc.queryForObject("SELECT version FROM crm_customer WHERE id=?",Long.class,customer);
        ok(call("PATCH","/api/customers/"+customer+"/follow-records/"+follow+"/review",manager,Map.of("version",cv,"reviewStatus","RETURNED","reviewReason","无实际沟通依据")));
        assertThat(jdbc.queryForObject("SELECT last_effective_progress_at FROM crm_opportunity WHERE id=?",LocalDateTime.class,id)).isEqualTo(baseline);
        assertThat(detail(peer,id).path("followRecords").get(0).path("effective").asBoolean()).isFalse();
        assertThat(detail(sales,id).path("stageProgress").get(1).path("requirements").get(0).path("met").asBoolean()).isFalse();
        status(stage(admin,id,"QUOTED","跟进已打回"),400);
        now=baseline.plusDays(30);doReturn(now.atZone(ZoneId.of("Asia/Shanghai")).toInstant()).when(businessClock).instant();
        doReturn(List.of(id)).when(opportunities).activityCandidates();
        activity.scanNow(admin);
        assertThat(detail(sales,id).path("activityStatus").asText()).isEqualTo("STAGNANT");
    }

    @Test void funnelCountsAmountsAndRoleFiltersAreExact() {
        long id=create(admin,customer,"漏斗1",100);create(admin,customer,"漏斗2",200);follow(sales,id,"NEED_CONFIRMED");
        long other=customer(foreign,south,"FOLLOWING","PRIVATE");create(admin,other,"其他团队",900);
        JsonNode rows=ok(call("GET","/api/opportunities/funnel?ownerId="+sales,manager,null));
        assertThat(rows.size()).isEqualTo(2);
        assertThat(rows.get(0).path("expectedAmount").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(ok(call("GET","/api/opportunities/funnel?ownerId="+foreign,manager,null)).size()).isZero();
        assertThat(ok(call("GET","/api/opportunities/funnel?ownerId="+sales,peer,null)).size()).isZero();
        status(call("GET","/api/opportunities/funnel?from=2026-12-01&to=2026-10-01",sales,null),400);
        assertThat(ok(call("GET","/api/opportunities/funnel?from=2026-12-01",sales,null)).size()).isZero();
    }

    @Test void concurrentStageRequestsOnlyCommitOnceAndAuditFailureRollsBack() throws Exception {
        long id=create(admin,customer,"并发",100),fresh=create(admin,customer,"自动事务回滚",100);String auth=token(admin);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var a=pool.submit(() -> {start.await();return raw("PATCH","/api/opportunities/"+id+"/stage",auth,Map.of("version",0,"targetStage","LOST","reason","并发管理员操作")).getStatusCode().value();});
            var b=pool.submit(() -> {start.await();return raw("PATCH","/api/opportunities/"+id+"/stage",auth,Map.of("version",0,"targetStage","LOST","reason","并发管理员操作")).getStatusCode().value();});
            start.countDown();assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally {pool.shutdownNow();}
        long before=version(fresh);
        long customerBefore=jdbc.queryForObject("SELECT version FROM crm_customer WHERE id=?",Long.class,customer);
        doThrow(new DataIntegrityViolationException("Injected opportunity audit failure")).when(opportunities).audit(eq(fresh),eq("OPPORTUNITY_STAGE_CHANGED"),any(),any(),any(),any(),any(),any());
        status(call("POST","/api/opportunities/"+fresh+"/follow-records",sales,followBody(sales,fresh,"NEED_CONFIRMED")),409);
        assertThat(version(fresh)).isEqualTo(before);
        assertThat(detail(manager,fresh).path("stageCode").asText()).isEqualTo("NEW");
        assertThat(detail(manager,fresh).path("stageHistory").size()).isEqualTo(1);
        assertThat(detail(manager,fresh).path("followRecords").size()).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM crm_customer WHERE id=?",Long.class,customer)).isEqualTo(customerBefore);
    }

    @Test void scanBoundaryEscalationDedupAndDecisionAreRepeatable() {
        long id=create(admin,customer,"扫描",100);doReturn(List.of(id)).when(opportunities).activityCandidates();
        jdbc.update("UPDATE crm_opportunity SET stage_changed_at=?,last_effective_progress_at=? WHERE id=?",now.minusDays(20),now.minusDays(20),id);
        assertThat(activity.scanNow(admin).get("changed")).isEqualTo(1);
        assertThat(detail(manager,id).path("activityStatus").asText()).isEqualTo("STAGNANT");
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM crm_todo WHERE biz_type='OPPORTUNITY' AND biz_id=?",Integer.class,id);
        activity.scanNow(admin);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_todo WHERE biz_type='OPPORTUNITY' AND biz_id=?",Integer.class,id)).isEqualTo(count);
        jdbc.update("UPDATE crm_opportunity SET stage_changed_at=?,last_effective_progress_at=? WHERE id=?",now.minusDays(30),now.minusDays(30),id);
        activity.scanNow(admin);
        assertThat(detail(manager,id).path("activityStatus").asText()).isEqualTo("CLOSE_PENDING");
        jdbc.update("UPDATE crm_opportunity SET close_pending_at=? WHERE id=?",now.minusDays(3),id);
        activity.scanNow(admin);
        var recipients=jdbc.queryForList("SELECT recipient_id FROM crm_todo WHERE biz_type='OPPORTUNITY' AND biz_id=? AND todo_type='OPP_ESCALATED'",Long.class,id);
        assertThat(recipients).contains(admin,manager).doesNotContain(sales,peer,foreign);
        assertThat(recipients).doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject("SELECT sea_type FROM crm_customer WHERE id=?",String.class,customer)).isEqualTo("PRIVATE");
        status(call("POST","/api/opportunities/"+id+"/close-decision",manager,Map.of("version",version(id),"close",true,"reason","不能借裁决关闭")),403);
        ok(call("POST","/api/opportunities/"+id+"/close-decision",manager,Map.of("version",version(id),"close",false,"reason","已核实仍有合作意向")));
        assertThat(detail(manager,id).path("activityStatus").asText()).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_todo WHERE biz_type='OPPORTUNITY' AND biz_id=? AND todo_status='PENDING'",Integer.class,id)).isZero();
    }

    @Test void scanFailureIsIsolatedAndLoggedWithoutPartialAudit() {
        long first=create(admin,customer,"失败",100),second=create(admin,customer,"成功",100);
        doReturn(List.of(first,second)).when(opportunities).activityCandidates();
        jdbc.update("UPDATE crm_opportunity SET stage_changed_at=?,last_effective_progress_at=? WHERE id IN (?,?)",now.minusDays(31),now.minusDays(31),first,second);
        doThrow(new DataIntegrityViolationException("Injected scan audit failure")).when(opportunities).audit(eq(first),eq("OPPORTUNITY_ACTIVITY_CHANGED"),any(),any(),any(),any(),any(),any());
        var result=activity.scanNow(admin);
        assertThat(result.get("failedIds")).isEqualTo(List.of(first));
        assertThat(detail(manager,first).path("activityStatus").asText()).isEqualTo("ACTIVE");
        assertThat(detail(manager,second).path("activityStatus").asText()).isEqualTo("CLOSE_PENDING");
        assertThat(jdbc.queryForObject("SELECT run_status FROM crm_job_log WHERE triggered_by=? ORDER BY id DESC LIMIT 1",String.class,admin)).isEqualTo("PARTIAL_FAILURE");
    }

    @Test void allRolesSeeCurrentStageAndCompletedAndMissingRequirementsWithoutBroaderAccess() {
        long id=create(admin,customer,"门槛提示",100);
        for (Long actor : List.of(sales,manager,admin)) {
            JsonNode d=detail(actor,id);
            assertThat(d.path("stageProgress").size()).isEqualTo(7);
            assertThat(d.path("stageProgress").get(0).path("current").asBoolean()).isTrue();
            assertThat(d.path("stageProgress").get(0).path("requirements").get(0).path("met").asBoolean()).isFalse();
            assertThat(d.path("canChangeStage").asBoolean()).isEqualTo(actor.equals(admin));
        }
        status(call("GET","/api/opportunities/"+id,foreign,null),404);
        follow(sales,id,"NEED_CONFIRMED");
        for (Long actor : List.of(sales,manager,admin)) {
            JsonNode progress=detail(actor,id).path("stageProgress").get(1);
            assertThat(progress.path("current").asBoolean()).isTrue();
            assertThat(progress.path("requirements").get(0).path("met").asBoolean()).isTrue();
            assertThat(progress.path("requirements").get(1).path("met").asBoolean()).isFalse();
        }
        evidence(sales,id,"QUOTE",100,"门槛报价");
        assertThat(detail(manager,id).path("stageProgress").get(2).path("requirements").get(1).path("met").asBoolean()).isTrue();
        assertThat(detail(manager,id).path("stageProgress").get(2).path("requirements").get(2).path("met").asBoolean()).isFalse();
    }

    @Test void onlyAdminCreatesWithConsistentActiveManagerOrSpecialistOwnership() {
        var body=payload(customer,"权限分配",100);body.put("ownerId",sales);
        for(Long actor:List.of(sales,manager,peer,foreign)) status(call("POST","/api/opportunities",actor,body),403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_opportunity WHERE customer_id=?",Long.class,customer)).isZero();
        assertThat(jdbc.queryForObject("SELECT customer_status FROM crm_customer WHERE id=?",String.class,customer)).isEqualTo("FOLLOWING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_operation_log WHERE operator_id IN (?,?,?,?,?)",Long.class,admin,sales,manager,peer,foreign)).isZero();
        long id=ok(call("POST","/api/opportunities",admin,body)).asLong();
        assertThat(detail(sales,id).path("ownerId").asLong()).isEqualTo(sales);
        long owned=customer(manager,east,"FOLLOWING","PRIVATE");
        var managerBody=payload(owned,"经理自有",200);managerBody.put("ownerId",manager);
        long managed=ok(call("POST","/api/opportunities",admin,managerBody)).asLong();
        assertThat(detail(manager,managed).path("ownerId").asLong()).isEqualTo(manager);
        assertThat(detail(admin,managed).path("teamId").asLong()).isEqualTo(east);
        status(call("GET","/api/opportunities/"+managed,sales,null),404);
        managerBody.put("ownerId",sales);status(call("POST","/api/opportunities",admin,managerBody),409);
        assertThat(jdbc.queryForObject("SELECT owner_id FROM crm_customer WHERE id=?",Long.class,owned)).isEqualTo(manager);
        jdbc.update("UPDATE sys_user SET account_status='DISABLED' WHERE id=?",manager);
        managerBody.put("ownerId",manager);status(call("POST","/api/opportunities",admin,managerBody),400);
        long mismatched=customer(peer,south,"FOLLOWING","PRIVATE");
        status(call("POST","/api/opportunities",admin,payload(mismatched,"团队不一致",1)),400);
        long invalid=customer(admin,null,"FOLLOWING","PRIVATE");
        status(call("POST","/api/opportunities",admin,payload(invalid,"管理员不能负责",1)),400);
    }

    @Test void opportunityAndOtherPagedEndpointsUseTenRows() {
        for(int i=0;i<12;i++) create(admin,customer,"分页"+i,100);
        var first=ok(call("GET","/api/opportunities?page=1",sales,null));
        var second=ok(call("GET","/api/opportunities?page=2",sales,null));
        assertThat(first.path("total").asInt()).isEqualTo(12);assertThat(first.path("list").size()).isEqualTo(10);assertThat(second.path("list").size()).isEqualTo(2);
        Set<Long> ids=new HashSet<>();first.path("list").forEach(x->ids.add(x.path("id").asLong()));second.path("list").forEach(x->ids.add(x.path("id").asLong()));assertThat(ids).hasSize(12);
        for(String path:List.of("/api/opportunities","/api/users","/api/contracts","/api/contracts/registration-opportunities")) {
            status(call("GET",path+"?size=20",admin,null),400);
            status(call("GET",path+"?size=10",admin,null),200);
        }
    }

    Map<String,Object> followBody(Long actor,long id,String result) {
        JsonNode d=detail(actor,id);
        return Map.of("customerVersion",d.path("customerVersion").asLong(),"contactId",d.path("contacts").get(0).path("id").asLong(),
                "followMethod","PHONE","content","已沟通项目需求和具体计划","resultCode",result,"substantiveResult","客户需求和采购时间已确认",
                "nextAction","发送项目方案","nextActionAt",now.plusDays(1).toString(),"followedAt",now.toString());
    }
    void follow(Long actor,long id,String result) {ok(call("POST","/api/opportunities/"+id+"/follow-records",actor,followBody(actor,id,result)));}
    long create(Long actor,Long c,String name,int amount) {return ok(call("POST","/api/opportunities",actor,payload(c,name,amount))).asLong();}
    Map<String,Object> payload(Long c,String name,int amount) {return new HashMap<>(Map.of("customerId",c,"opportunityName",prefix+name,"expectedAmount",amount,"expectedCloseDate","2026-12-01"));}
    long version(long id) {return jdbc.queryForObject("SELECT version FROM crm_opportunity WHERE id=?",Long.class,id);}
    JsonNode detail(Long actor,long id) {return ok(call("GET","/api/opportunities/"+id,actor,null));}
    ResponseEntity<JsonNode> stage(Long actor,long id,String to,String reason) {var body=new HashMap<String,Object>(Map.of("version",version(id),"targetStage",to));if(reason!=null)body.put("reason",reason);return call("PATCH","/api/opportunities/"+id+"/stage",actor,body);}
    void evidence(Long actor,long id,String kind,int amount,String text) {ok(call("POST","/api/opportunities/"+id+"/evidence",actor,Map.of("version",version(id),"kind",kind,"quoteAmount",amount,"description",text)));}
    void addMember(Long actor,long id,Long member,int ratio) {ok(call("POST","/api/opportunities/"+id+"/collaborators",actor,Map.of("version",version(id),"userId",member,"splitRatio",ratio)));}
    Long team(String suffix) {jdbc.update("INSERT INTO sys_team(team_code,team_name) VALUES(?,?)",prefix+suffix,prefix+suffix);return jdbc.queryForObject("SELECT id FROM sys_team WHERE team_code=?",Long.class,prefix+suffix);}
    Long user(String suffix,String role,Long team) {jdbc.update("INSERT INTO sys_user(username,password_hash,real_name,role_code,team_id,must_change_password) VALUES(?,?,?,?,?,0)",prefix+suffix,passwords.encode("OpportunityFixture123!"),suffix,role,team);Long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,prefix+suffix);actors.add(id);return id;}
    Long customer(Long owner,Long team,String state,String sea) {
        String name=prefix+"-"+customers.size();
        jdbc.update("INSERT INTO crm_customer(customer_name,normalized_name,owner_id,team_id,customer_status,sea_type,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?)",name,name,owner,team,state,sea,admin,admin);
        Long id=jdbc.queryForObject("SELECT id FROM crm_customer WHERE customer_name=?",Long.class,name);customers.add(id);
        jdbc.update("INSERT INTO crm_contact(customer_id,contact_name,phone,is_primary) VALUES(?,'临时联系人','13812345678',1)",id);return id;
    }
    String token(Long actor) {return tokens.issue(AuthPrincipal.from(users.findWithTeamById(actor))).value();}
    ResponseEntity<JsonNode> call(String method,String url,Long actor,Object body) {return raw(method,url,actor==null?null:token(actor),body);}
    ResponseEntity<JsonNode> raw(String method,String url,String token,Object body) {var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);if(token!=null)headers.setBearerAuth(token);return http.exchange(url,HttpMethod.valueOf(method),new HttpEntity<>(body,headers),JsonNode.class);}
    JsonNode ok(ResponseEntity<JsonNode> response) {status(response,200);assertThat(response.getBody().path("code").asInt()).isZero();return response.getBody().path("data");}
    void status(ResponseEntity<JsonNode> response,int expected) {assertThat(response.getStatusCode().value()).withFailMessage("%s",response.getBody()).isEqualTo(expected);}
}
