/* 文件作用：真实数据库验证提醒边界、日志脱敏和完整交接的权限、并发与回滚。 */
package com.bishe.crm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.customer.mapper.*;
import com.bishe.crm.module.task.mapper.*;
import com.bishe.crm.module.task.service.*;
import com.bishe.crm.module.contract.service.PaymentService;
import com.bishe.crm.module.contract.mapper.PaymentMapper;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class CompletionFlowIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    @Autowired SystemUserMapper users;
    @Autowired PaymentService payments;
    @Autowired PaymentMapper paymentMapper;
    @Autowired Clock clock;
    @Autowired TaskMapper taskMapper;
    @Autowired com.bishe.crm.module.customer.service.HandoverService handoverService;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired JobExecutionService jobs;
    @Autowired CustomerMapper customers;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ScheduledAnnotationBeanPostProcessor scheduler;
    @MockitoSpyBean BusinessReminderMapper reminders;
    @MockitoSpyBean HandoverMapper handovers;
    PaymentFlowIT f;
    BusinessReminderService service;
    LocalDateTime now;

    /** 隔离团队和业务，不运行本测试实例的定时任务。 */
    @BeforeEach void setup() {
        scheduler.destroy();
        f=new PaymentFlowIT();f.http=http;f.jdbc=jdbc;f.tokens=tokens;f.users=users;f.payments=payments;f.mapper=paymentMapper;f.businessClock=clock;f.setup();
        Clock fixed=Clock.fixed(clock.instant().truncatedTo(ChronoUnit.MILLIS),clock.getZone());now=LocalDateTime.now(fixed);
        service=new BusinessReminderService(reminders,customers,jobs,fixed,transactions);
        doReturn(List.of(f.customer)).when(reminders).candidates();
    }
    /** 精确清理关联日志、提醒、跟进和协作，再移除隔离fixture。 */
    @AfterEach void cleanup() {
        reset(reminders,handovers);
        jdbc.update("DELETE FROM crm_contact WHERE customer_id=?",f.customer);
        jdbc.update("DELETE FROM crm_follow_record WHERE customer_id=?",f.customer);
        jdbc.update("DELETE FROM crm_opportunity_collaborator WHERE opportunity_id=?",f.opportunity);
        jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=? OR biz_type='OPPORTUNITY' AND biz_id=?",f.customer,f.opportunity);
        jdbc.update("DELETE FROM crm_todo WHERE recipient_id IN(?,?,?,?)",f.sales,f.peer,f.manager,f.admin);
        jdbc.update("DELETE FROM crm_todo WHERE biz_type='CUSTOMER' AND biz_id=? OR biz_type='CONTRACT' AND biz_id=? OR biz_type='OPPORTUNITY' AND biz_id=?",f.customer,f.contract,f.opportunity);
        jdbc.update("DELETE l FROM crm_operation_log l JOIN crm_job_log j ON l.biz_type='JOB' AND l.biz_id=j.id WHERE j.triggered_by=?",f.admin);
        jdbc.update("DELETE FROM crm_job_log WHERE triggered_by=?",f.admin);
        f.cleanup();
    }
    /** 未到约定毫秒不提醒，恰好到期提醒；重扫保留已读且不重复。 */
    @Test void followBoundaryDedupAndReadPreserved() {
        long follow=follow(now.plusSeconds(1),now.minusDays(1));scan();assertThat(count("FOLLOW_DUE","PENDING")).isZero();
        jdbc.update("UPDATE crm_follow_record SET next_action_at=? WHERE id=?",now,follow);scan();
        assertThat(count("FOLLOW_DUE","PENDING")).isEqualTo(1);
        jdbc.update("UPDATE crm_todo SET read_at=? WHERE recipient_id=? AND todo_type='FOLLOW_DUE'",now,f.sales);
        scan();assertThat(count("FOLLOW_DUE","PENDING")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT read_at FROM crm_todo WHERE recipient_id=? AND todo_type='FOLLOW_DUE'",LocalDateTime.class,f.sales)).isEqualTo(now);
    }
    /** 新计划替代旧计划，已解除事项不因旧跟进再次提醒。 */
    @Test void nextFollowResolvesOldPlan() {
        follow(now.minusHours(1),now.minusDays(2));scan();assertThat(count("FOLLOW_DUE","PENDING")).isEqualTo(1);
        follow(now.plusDays(1),now);scan();assertThat(count("FOLLOW_DUE","PENDING")).isZero();assertThat(count("FOLLOW_DUE","RESOLVED")).isEqualTo(1);
    }
    /** 跨日生成新一天提醒，旧一天解除；最终成交客户不继续催跟进。 */
    @Test void nextDayAndPermanentCustomer() {
        follow(now.minusHours(1),now.minusDays(1));scan();
        Clock nextClock=Clock.fixed(now.plusDays(1).atZone(clock.getZone()).toInstant(),clock.getZone());
        var next=new BusinessReminderService(reminders,customers,new JobExecutionService(taskMapper,json,nextClock,transactions),nextClock,transactions);
        next.scan(now.toLocalDate().plusDays(1),f.admin,null);
        assertThat(count("FOLLOW_DUE","PENDING")).isEqualTo(1);assertThat(count("FOLLOW_DUE","RESOLVED")).isEqualTo(1);
        jdbc.update("UPDATE crm_customer SET permanent_deal_at=? WHERE id=?",now,f.customer);scan();assertThat(count("FOLLOW_DUE","PENDING")).isZero();
    }
    /** 合同30天边界、多接收人、完成后自动解除。 */
    @Test void contractBoundaryRecipientsAndResolution() {
        jdbc.update("UPDATE crm_contract SET contract_status='DRAFT',signing_progress='DRAFT_GENERATED',created_at=? WHERE id=?",now.minusDays(30).plusSeconds(1),f.contract);
        scan();assertThat(count("CONTRACT_STAGNANT","PENDING")).isZero();
        jdbc.update("UPDATE crm_contract SET created_at=? WHERE id=?",now.minusDays(30),f.contract);scan();
        assertThat(count("CONTRACT_STAGNANT","PENDING")).isEqualTo(2);scan();assertThat(count("CONTRACT_STAGNANT","PENDING")).isEqualTo(2);
        jdbc.update("UPDATE crm_contract SET signing_progress='FILE_CONFIRMED',contract_status='SIGNED' WHERE id=?",f.contract);scan();
        assertThat(count("CONTRACT_STAGNANT","PENDING")).isZero();
    }
    /** 实际办理事件刷新停滞计时，财务更新不混入办理进展。 */
    @Test void contractProgressResetsStagnation() {
        jdbc.update("UPDATE crm_contract SET contract_status='DRAFT',signing_progress='DRAFT_GENERATED',created_at=? WHERE id=?",now.minusDays(31),f.contract);scan();
        jdbc.update("INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,occurred_at) VALUES('CONTRACT',?,'CONTRACT_FILE_UPLOADED',?,?)",f.contract,f.sales,now);scan();assertThat(count("CONTRACT_STAGNANT","PENDING")).isZero();
    }
    /** 使用现有生日字段当天提醒且不重复，改为其他日期后解除。 */
    @Test void birthdayCareDeduplicatesAndResolves() {
        jdbc.update("INSERT INTO crm_contact(customer_id,contact_name,birthday) VALUES(?,'隔离联系人',?)",f.customer,now.toLocalDate().minusYears(20));
        scan();scan();assertThat(count("CARE_BIRTHDAY","PENDING")).isEqualTo(1);
        jdbc.update("UPDATE crm_contact SET birthday=? WHERE customer_id=?",now.toLocalDate().minusYears(20).plusDays(1),f.customer);
        scan();assertThat(count("CARE_BIRTHDAY","PENDING")).isZero();
    }
    /** 登记和文件核验交给非负责经理，环节变化后旧待办解除。 */
    @Test void contractReviewRecipientsAndProgress() {
        jdbc.update("UPDATE crm_contract SET contract_status='PENDING_REVIEW',signing_progress='DRAFT_REVIEW' WHERE id=?",f.contract);scan();
        assertThat(count("CONTRACT_REVIEW","PENDING")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT recipient_id FROM crm_todo WHERE biz_id=? AND todo_type='CONTRACT_REVIEW' AND todo_status='PENDING'",Long.class,f.contract)).isEqualTo(f.manager);
        jdbc.update("UPDATE crm_contract SET signing_progress='SIGNED_UPLOAD' WHERE id=?",f.contract);scan();assertThat(count("CONTRACT_REVIEW","PENDING")).isZero();
        jdbc.update("UPDATE crm_contract SET owner_id=?,signing_progress='FILE_REVIEW' WHERE id=?",f.manager,f.contract);scan();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_todo WHERE biz_id=? AND todo_type='CONTRACT_REVIEW' AND recipient_id=? AND todo_status='PENDING'",Integer.class,f.contract,f.admin)).isEqualTo(1);
        assertThat(count("CONTRACT_REVIEW","PENDING")).isZero();
    }
    /** 新提醒任务可由管理员执行；同样拒绝经理直接触发全局扫描。 */
    @Test void manualReminderEntryAndAuthorization() {
        assertThat(call("POST","/api/tasks/jobs/scan",f.manager,Map.of("jobName","business-reminder-scan")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ok(call("POST","/api/tasks/jobs/scan",f.admin,Map.of("jobName","business-reminder-scan"))).path("status").asText()).isEqualTo("SUCCESS");
    }
    /** 单客户入口也必须完整移动关联业务，不能留下原商机负责人。 */
    @Test void singleTransferUsesWholeBusinessHandover() {
        ok(call("PATCH","/api/customers/"+f.customer+"/ownership",f.admin,Map.of("version",0,"newOwnerId",f.peer,"reason","隔离验证单客户完整交接")));
        assertThat(jdbc.queryForObject("SELECT owner_id FROM crm_opportunity WHERE id=?",Long.class,f.opportunity)).isEqualTo(f.peer);
        assertThat(jdbc.queryForObject("SELECT owner_id FROM crm_contract WHERE id=?",Long.class,f.contract)).isEqualTo(f.peer);
    }
    /** 完整交接保留历史业绩，迁移未处理待办，原负责人丧失合同访问。 */
    @Test void handoverMovesBusinessAndPreservesHistory() {
        jdbc.update("UPDATE crm_contract SET performance_owner_id=?,performance_team_id=?,performance_confirmed_at=?,performance_amount=1000 WHERE id=?",f.sales,f.team,now,f.contract);
        follow(now.minusMinutes(1),now.minusDays(1));scan();
        var p=ok(call("POST","/api/customers/handover/preview",f.admin,selection()));
        assertThat(p.path("opportunities").asInt()).isEqualTo(1);assertThat(p.path("contracts").asInt()).isEqualTo(1);
        handoverService.execute(AuthPrincipal.from(users.findWithTeamById(f.admin)),new com.bishe.crm.module.customer.dto.HandoverRequests.Execute(new com.bishe.crm.module.customer.dto.HandoverRequests.Selection(f.sales,f.peer,List.of(f.customer)),p.path("previewToken").asText(),"隔离验证完整交接"));
        for(String table:List.of("crm_customer","crm_opportunity","crm_contract"))assertThat(jdbc.queryForObject("SELECT owner_id FROM "+table+" WHERE id=?",Long.class,table.equals("crm_customer")?f.customer:table.equals("crm_opportunity")?f.opportunity:f.contract)).isEqualTo(f.peer);
        assertThat(jdbc.queryForObject("SELECT performance_owner_id FROM crm_contract WHERE id=?",Long.class,f.contract)).isEqualTo(f.sales);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_todo WHERE recipient_id=? AND todo_type='FOLLOW_DUE' AND todo_status='PENDING'",Integer.class,f.peer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT current_hold_count FROM sys_user WHERE id=?",Integer.class,f.peer)).isEqualTo(1);
        assertThat(call("GET","/api/contracts/"+f.contract,f.sales,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ok(call("GET","/api/contracts/"+f.contract,f.peer,null));ok(call("GET","/api/customers/"+f.customer,f.sales,null));
        assertThat(call("POST","/api/customers/handover",f.admin,execution(p)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    /** 预览后相关业务版本变化，拒绝过期执行；非管理员不允许预览或执行。 */
    @Test void handoverStalePreviewAndPermissions() {
        for(Long user:List.of(f.sales,f.manager))assertThat(call("POST","/api/customers/handover/preview",user,selection()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var p=ok(call("POST","/api/customers/handover/preview",f.admin,selection()));jdbc.update("UPDATE crm_contract SET version=version+1 WHERE id=?",f.contract);
        assertThat(call("POST","/api/customers/handover",f.admin,execution(p)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT owner_id FROM crm_customer WHERE id=?",Long.class,f.customer)).isEqualTo(f.sales);
    }
    /** 任一关联更新异常，客户、商机、合同、审计均回滚。 */
    @Test void handoverRollbackOnLinkedFailure() {
        var p=ok(call("POST","/api/customers/handover/preview",f.admin,selection()));
        doThrow(new IllegalStateException("isolated handover rollback")).when(handovers).moveContract(eq(f.contract),anyLong(),eq(f.peer),eq(f.team));
        assertThat(call("POST","/api/customers/handover",f.admin,execution(p)).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(jdbc.queryForObject("SELECT owner_id FROM crm_customer WHERE id=?",Long.class,f.customer)).isEqualTo(f.sales);
        assertThat(jdbc.queryForObject("SELECT owner_id FROM crm_opportunity WHERE id=?",Long.class,f.opportunity)).isEqualTo(f.sales);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=?",Integer.class,f.customer)).isZero();
    }
    /** 并发确认相同预览只允许一批成功，另一批不能重复移动。 */
    @Test void concurrentHandover() throws Exception {
        var p=ok(call("POST","/api/customers/handover/preview",f.admin,selection()));var pool=Executors.newFixedThreadPool(2);
        try {var x=pool.submit(()->call("POST","/api/customers/handover",f.admin,execution(p)));var y=pool.submit(()->call("POST","/api/customers/handover",f.admin,execution(p)));
            assertThat(List.of(x.get(30,TimeUnit.SECONDS).getStatusCode(),y.get(30,TimeUnit.SECONDS).getStatusCode())).containsExactlyInAnyOrder(HttpStatus.OK,HttpStatus.CONFLICT);
        }finally{pool.shutdownNow();}
    }
    /** 日志详情不返回原始快照或敏感键，按当前业务范围再次授权。 */
    @Test void operationDetailWhitelistsAndScopes() {
        jdbc.update("INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,before_json,after_json) VALUES('CUSTOMER',?,'CUSTOMER_UPDATED',?, ?, ?)",f.customer,f.sales,"{\"customerName\":\"旧名称\",\"password\":\"never-return\",\"primaryPhone\":\"13812345678\"}","{\"customerName\":\"新名称\",\"primaryPhone\":\"13912345678\"}");
        long id=jdbc.queryForObject("SELECT MAX(id) FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=?",Long.class,f.customer);
        var result=ok(call("GET","/api/tasks/operations/"+id,f.sales,null));assertThat(result.path("changes").size()).isEqualTo(2);
        assertThat(result.toString()).doesNotContain("never-return","13812345678","13912345678","before_json").contains("138****5678");
        assertThat(call("GET","/api/tasks/operations/"+id,f.peer,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ok(call("GET","/api/tasks/operations/"+id,f.manager,null));
        assertThat(ok(call("GET","/api/tasks/operations?bizType=CUSTOMER&bizId="+f.customer,f.sales,null)).path("total").asInt()).isEqualTo(1);
        // 真实阶段编码为QUOTED；审计前后值必须显示中文，旧QUOTING仍兼容。
        jdbc.update("INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,before_json,after_json) VALUES('OPPORTUNITY',?,'OPPORTUNITY_STAGE_CHANGED',?,?,?)",f.opportunity,f.sales,"{\"stageCode\":\"FOLLOWING\"}","{\"stageCode\":\"QUOTED\"}");
        long stageLog=jdbc.queryForObject("SELECT MAX(id) FROM crm_operation_log WHERE biz_type='OPPORTUNITY' AND biz_id=?",Long.class,f.opportunity);
        var stageChange=ok(call("GET","/api/tasks/operations/"+stageLog,f.manager,null)).path("changes").get(0);
        assertThat(stageChange.path("before").asText()).isEqualTo("跟进");
        assertThat(stageChange.path("after").asText()).isEqualTo("报价");
    }
    /** 无快照的旧记录仍能查询，明确提示缺失而非伪造差异。 */
    @Test void oldAuditWithoutSnapshots() {
        jdbc.update("INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id) VALUES('CUSTOMER',?,'LEGACY_TEST',?)",f.customer,f.sales);
        long id=jdbc.queryForObject("SELECT MAX(id) FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=?",Long.class,f.customer);
        assertThat(ok(call("GET","/api/tasks/operations/"+id,f.sales,null)).path("changes").size()).isZero();
    }
    /** 移出方可以停用，接收人停用或跨团队则拒绝。 */
    @Test void inactiveSourceAndInvalidTarget() {
        jdbc.update("UPDATE sys_user SET account_status='DISABLED' WHERE id=?",f.peer);
        assertThat(call("POST","/api/customers/handover/preview",f.admin,selection()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        jdbc.update("UPDATE sys_user SET account_status='ACTIVE' WHERE id=?",f.peer);
        jdbc.update("UPDATE sys_user SET account_status='DISABLED' WHERE id=?",f.sales);
        ok(call("POST","/api/customers/handover/preview",f.admin,selection()));
    }
    /** 创建不依赖真实联系人资料的隔离跟进记录。 */
    long follow(LocalDateTime next,LocalDateTime at) {
        jdbc.update("INSERT INTO crm_follow_record(customer_id,follow_user_id,record_type,content,next_action,next_action_at,followed_at) VALUES(?,?,'CUSTOMER','隔离测试','继续沟通',?,?)",f.customer,f.sales,next,at);
        return jdbc.queryForObject("SELECT MAX(id) FROM crm_follow_record WHERE customer_id=?",Long.class,f.customer);
    }
    void scan(){assertThat(service.scan(now.toLocalDate(),f.admin,null).get("status")).isEqualTo("SUCCESS");}
    int count(String type,String status){return jdbc.queryForObject("SELECT COUNT(*) FROM crm_todo WHERE todo_type=? AND todo_status=? AND recipient_id IN(?,?,?)",Integer.class,type,status,f.sales,f.peer,f.manager);}
    Map<String,Object> selection(){return Map.of("fromOwnerId",f.sales,"toOwnerId",f.peer,"customerIds",List.of(f.customer));}
    Map<String,Object> execution(JsonNode p){return Map.of("selection",selection(),"previewToken",p.path("previewToken").asText(),"reason","隔离验证完整交接");}
    ResponseEntity<JsonNode> call(String method,String url,Long actor,Object body) {var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.setBearerAuth(tokens.issue(AuthPrincipal.from(users.findWithTeamById(actor))).value());return http.exchange(url,HttpMethod.valueOf(method),new HttpEntity<>(body,headers),JsonNode.class);}
    JsonNode ok(ResponseEntity<JsonNode> r){assertThat(r.getStatusCode()).withFailMessage("%s",r.getBody()).isEqualTo(HttpStatus.OK);return r.getBody().path("data");}
}
