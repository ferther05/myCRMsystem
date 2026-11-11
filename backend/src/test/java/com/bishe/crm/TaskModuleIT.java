/* 文件作用：以隔离数据验证任务中心权限、已读语义、业务跳转与失败项补跑。 */
package com.bishe.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.contract.mapper.PaymentMapper;
import com.bishe.crm.module.contract.service.PaymentService;
import com.bishe.crm.module.opportunity.mapper.OpportunityMapper;
import com.bishe.crm.module.opportunity.service.OpportunityActivityService;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import com.bishe.crm.module.task.service.JobExecutionService;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class TaskModuleIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired SystemUserMapper users;
    @Autowired JwtTokenService tokens;
    @Autowired Clock clock;
    @Autowired PaymentService payments;
    @Autowired PaymentMapper paymentMapper;
    @Autowired JobExecutionService jobs;
    @Autowired OpportunityActivityService activity;
    @MockitoSpyBean OpportunityMapper opportunities;
    PaymentFlowIT f;

    /** 重用独立合同fixture，不使用演示账号或演示合同办理业务。 */
    @BeforeEach void setup() {
        f=new PaymentFlowIT();f.http=http;f.jdbc=jdbc;f.tokens=tokens;f.users=users;f.payments=payments;f.mapper=paymentMapper;f.businessClock=clock;
        f.setup();
    }

    /** 精确清理本测试关联记录，避免任何全库删除或清空。 */
    @AfterEach void cleanup() {
        reset(opportunities);
        jdbc.update("DELETE l FROM crm_operation_log l JOIN crm_todo t ON l.biz_type='TODO' AND l.biz_id=t.id WHERE t.occurrence_key=?",f.prefix);
        jdbc.update("DELETE FROM crm_todo WHERE occurrence_key=?",f.prefix);
        var ids=new ArrayList<Long>(f.extraOpportunities);ids.add(f.opportunity);
        for(Long id:ids) {
            jdbc.update("DELETE FROM crm_todo WHERE biz_type='OPPORTUNITY' AND biz_id=?",id);
            jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='OPPORTUNITY' AND biz_id=?",id);
        }
        jdbc.update("DELETE l FROM crm_operation_log l JOIN crm_job_log j ON l.biz_type='JOB' AND l.biz_id=j.id WHERE j.triggered_by=?",f.admin);
        jdbc.update("DELETE FROM crm_job_log WHERE triggered_by=?",f.admin);
        jdbc.update("DELETE FROM crm_operation_log WHERE action_code=?",f.prefix);
        f.cleanup();
    }

    /** 导航红圈只统计本人的未读待处理；已读仍待办与已解除分别处理。 */
    @Test void personalSummaryCountsUnreadAndPendingSeparately() {
        for(Long actor:List.of(f.sales,f.manager,f.admin)) {
            var empty=ok(call("GET","/summary",actor,null));
            assertThat(empty.path("unreadCount").asLong()).isZero();
            assertThat(empty.path("pendingCount").asLong()).isZero();
        }
        var today=LocalDate.now(clock);
        long first=todo(f.sales,"OPPORTUNITY",f.opportunity,"OPP_STAGNANT",today);
        long second=todo(f.sales,"OPPORTUNITY",f.opportunity,"OPP_STAGNANT",today.minusDays(1));
        long resolved=todo(f.sales,"OPPORTUNITY",f.opportunity,"OPP_STAGNANT",today.minusDays(2));
        long cancelled=todo(f.sales,"OPPORTUNITY",f.opportunity,"OPP_STAGNANT",today.minusDays(3));
        long done=todo(f.sales,"OPPORTUNITY",f.opportunity,"OPP_STAGNANT",today.minusDays(4));
        jdbc.update("UPDATE crm_todo SET todo_status='RESOLVED' WHERE id=?",resolved);
        jdbc.update("UPDATE crm_todo SET todo_status='CANCELLED' WHERE id=?",cancelled);
        jdbc.update("UPDATE crm_todo SET todo_status='DONE' WHERE id=?",done);
        todo(f.manager,"OPPORTUNITY",f.opportunity,"OPP_STAGNANT",today);
        todo(f.admin,"OPPORTUNITY",f.opportunity,"OPP_STAGNANT",today);
        todo(f.peer,"OPPORTUNITY",f.opportunity,"OPP_STAGNANT",today);
        var sales=ok(call("GET","/summary?recipientId="+f.peer,f.sales,null));
        assertThat(sales.path("unreadCount").asLong()).isEqualTo(2);
        assertThat(sales.path("pendingCount").asLong()).isEqualTo(2);
        for(Long actor:List.of(f.manager,f.admin))
            assertThat(ok(call("GET","/summary",actor,null)).path("pendingCount").asLong()).isEqualTo(1);
        ok(call("POST","/todos/"+first+"/read",f.sales,Map.of("version",0)));
        assertThat(ok(call("GET","/summary",f.sales,null)).path("unreadCount").asLong()).isEqualTo(1);
        ok(call("POST","/todos/"+second+"/read",f.sales,Map.of("version",0)));
        var dot=ok(call("GET","/summary",f.sales,null));
        assertThat(dot.path("unreadCount").asLong()).isZero();
        assertThat(dot.path("pendingCount").asLong()).isEqualTo(2);
        jdbc.update("UPDATE crm_todo SET todo_status='RESOLVED' WHERE id IN (?,?)",first,second);
        assertThat(ok(call("GET","/summary",f.sales,null)).path("pendingCount").asLong()).isZero();
        assertThat(http.getForEntity("/api/tasks/summary",JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** 本人分页十条；不能伪造接收人，日期/分页参数不合法会拒绝。 */
    @Test void personalPaginationAndQueryValidation() {
        for(int i=0;i<12;i++) todo(f.sales,"OPPORTUNITY",f.opportunity,"OPP_STAGNANT",LocalDate.now(clock).minusDays(i));
        long other=todo(f.peer,"OPPORTUNITY",f.opportunity,"OPP_STAGNANT",LocalDate.now(clock));
        var data=ok(call("GET","/todos?recipientId="+f.peer,f.sales,null));
        assertThat(data.path("total").asInt()).isEqualTo(12);
        assertThat(data.path("list").size()).isEqualTo(10);
        assertThat(ok(call("GET","/todos?page=2",f.sales,null)).path("list").size()).isEqualTo(2);
        assertThat(call("POST","/todos/"+other+"/read",f.sales,Map.of("version",0)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        for(String query:List.of("size=100","page=0","status=BAD","from=2026-12-01&to=2026-01-01"))
            assertThat(call("GET","/todos?"+query,f.sales,null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** 已读不等于业务完成，不影响其他接收人或真实欠款；旧版本拒绝。 */
    @Test void readDoesNotResolveBusinessOrOtherRecipient() {
        long a=todo(f.sales,"OPPORTUNITY",f.opportunity,"OPP_STAGNANT",LocalDate.now(clock));
        long b=todo(f.manager,"OPPORTUNITY",f.opportunity,"OPP_STAGNANT",LocalDate.now(clock));
        ok(call("POST","/todos/"+a+"/read",f.sales,Map.of("version",0)));
        assertThat(jdbc.queryForObject("SELECT todo_status FROM crm_todo WHERE id=?",String.class,a)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT read_at FROM crm_todo WHERE id=?",LocalDateTime.class,a)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT read_at FROM crm_todo WHERE id=?",LocalDateTime.class,b)).isNull();
        assertThat(jdbc.queryForObject("SELECT outstanding_amount FROM crm_contract WHERE id=?",BigDecimal.class,f.contract)).isEqualByComparingTo("1000");
        assertThat(ok(call("GET","/todos?unreadOnly=true",f.sales,null)).path("total").asInt()).isZero();
        assertThat(call("POST","/todos/"+a+"/read",f.sales,Map.of("version",0)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        jdbc.update("UPDATE crm_todo SET todo_status='RESOLVED',resolved_at=NOW(),version=version+1 WHERE id IN (?,?)",a,b);
        assertThat(ok(call("GET","/todos?status=RESOLVED",f.sales,null)).path("list").get(0).path("readAt").isNull()).isFalse();
    }

    /** 业务链接点击时重新授权，失去归属后隐藏原业务标题并拒绝跳转。 */
    @Test void businessJumpIsReauthorized() {
        long id=todo(f.sales,"OPPORTUNITY",f.opportunity,"OPP_STAGNANT",LocalDate.now(clock));
        assertThat(ok(call("GET","/todos/"+id+"/target",f.sales,null)).path("path").asText()).isEqualTo("/opportunities?id="+f.opportunity);
        jdbc.update("UPDATE crm_opportunity SET owner_id=? WHERE id=?",f.peer,f.opportunity);
        assertThat(call("GET","/todos/"+id+"/target",f.sales,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var row=ok(call("GET","/todos",f.sales,null)).path("list").get(0);
        assertThat(row.path("target").isNull()).isTrue();
        assertThat(row.path("title").asText()).isEqualTo("关联业务已不可访问");
    }

    /** 回款期次和审批都定位到合同；已读后实际收齐仍由业务统一解除。 */
    @Test void paymentAndApprovalTargetsAndResolution() {
        long p=f.createPlan(1000,null);f.activate(p);long period=f.period(p);
        long id=todo(f.sales,"PAYMENT_PERIOD",period,"PAYMENT_OVERDUE",LocalDate.now(clock));
        assertThat(ok(call("GET","/todos/"+id+"/target",f.sales,null)).path("path").asText()).isEqualTo("/contracts?id="+f.contract);
        ok(call("POST","/todos/"+id+"/read",f.sales,Map.of("version",0)));
        long approval=jdbc.queryForObject("SELECT approval_id FROM crm_payment_plan WHERE id=?",Long.class,p);
        long review=todo(f.manager,"APPROVAL",approval,"APPROVAL",LocalDate.now(clock));
        assertThat(ok(call("GET","/todos/"+review+"/target",f.manager,null)).path("path").asText()).isEqualTo("/contracts?id="+f.contract);
        f.ok(f.call("POST","/payment-records",f.sales,f.receipt(period,1000,UUID.randomUUID().toString())));
        assertThat(jdbc.queryForObject("SELECT todo_status FROM crm_todo WHERE id=?",String.class,id)).isEqualTo("RESOLVED");
    }

    /** 操作摘要遵守当前账号范围，不返回敏感变化快照。 */
    @Test void operationScopesAndNoRawSnapshots() {
        for(Long user:List.of(f.sales,f.peer,f.admin))
            jdbc.update("INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,operator_team_id,reason,before_json) VALUES('USER',?,?,?,?,?,?)",user,f.prefix,user,user.equals(f.admin)?null:f.team,"测试摘要","{\"secret\":\"must-not-return\"}");
        var own=ok(call("GET","/operations?action="+f.prefix,f.sales,null));
        assertThat(own.path("total").asInt()).isEqualTo(1);
        assertThat(own.toString()).doesNotContain("beforeJson","must-not-return");
        assertThat(own.path("list").get(0).path("operatorName").asText()).isEqualTo(f.prefix+"sales");
        assertThat(ok(call("GET","/operations?action="+f.prefix,f.manager,null)).path("total").asInt()).isEqualTo(2);
        assertThat(ok(call("GET","/operations?action="+f.prefix,f.admin,null)).path("total").asInt()).isEqualTo(3);
    }

    /** 全局执行记录和扫描入口拒绝普通账号，扫描名称白名单生效。 */
    @Test void jobAdministrationPermissions() {
        for(Long user:List.of(f.sales,f.manager)) {
            assertThat(call("GET","/jobs",user,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(call("POST","/jobs/scan",user,Map.of("jobName","payment-overdue-scan")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(call("POST","/jobs/1/retry",user,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(call("POST","/jobs/scan",f.admin,Map.of("jobName","public-pool-recycle")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ok(call("GET","/jobs",f.admin,null));
    }

    /** 同日逾期重新发生时恢复未读并增加版本，重复扫描不覆盖已读状态。 */
    @Test void reopenedReminderResetsReadOnlyOnce() {
        long p=f.createPlan(1000,null);f.activate(p);long period=f.period(p);
        long id=todo(f.sales,"PAYMENT_PERIOD",period,"PAYMENT_OVERDUE",LocalDate.now(clock));
        ok(call("POST","/todos/"+id+"/read",f.sales,Map.of("version",0)));
        paymentMapper.insertOverdueTodo(period,f.prefix,LocalDate.now(clock),f.sales,"再次检查","test");
        assertThat(jdbc.queryForObject("SELECT read_at FROM crm_todo WHERE id=?",LocalDateTime.class,id)).isNotNull();
        jdbc.update("UPDATE crm_todo SET todo_status='RESOLVED',resolved_at=NOW(),version=version+1 WHERE id=?",id);
        paymentMapper.insertOverdueTodo(period,f.prefix,LocalDate.now(clock),f.sales,"再次逾期","test");
        assertThat(jdbc.queryForObject("SELECT read_at FROM crm_todo WHERE id=?",LocalDateTime.class,id)).isNull();
        assertThat(jdbc.queryForObject("SELECT version FROM crm_todo WHERE id=?",Long.class,id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT todo_status FROM crm_todo WHERE id=?",String.class,id)).isEqualTo("PENDING");
    }

    /** 第一条事务失败不留下部分业务，补跑只执行失败主键，历史尝试不覆盖。 */
    @Test void realFailureIsolationAndRetry() {
        jdbc.update("UPDATE crm_contract SET contract_status='DRAFT' WHERE id=?",f.contract);
        jdbc.update("UPDATE crm_opportunity SET stage_code='NEW',last_effective_progress_at=? WHERE id=?",LocalDateTime.now(clock).minusDays(60),f.opportunity);
        jdbc.update("INSERT INTO crm_opportunity(customer_id,opportunity_name,owner_id,team_id,stage_code,last_effective_progress_at,created_by) VALUES(?,?,?,?,'NEW',?,?)",f.customer,f.prefix+"second",f.sales,f.team,LocalDateTime.now(clock).minusDays(60),f.admin);
        long second=jdbc.queryForObject("SELECT id FROM crm_opportunity WHERE opportunity_name=?",Long.class,f.prefix+"second");f.extraOpportunities.add(second);
        doReturn(List.of(f.opportunity,second)).when(opportunities).activityCandidates();
        doThrow(new DataIntegrityViolationException("test audit rollback")).when(opportunities).audit(eq(f.opportunity),eq("OPPORTUNITY_ACTIVITY_CHANGED"),any(),any(),any(),any(),any(),any());
        var first=activity.scanSelected(LocalDate.now(clock),f.admin,null);
        long id=((Number)first.get("id")).longValue();
        assertThat(first.get("status")).isEqualTo("PARTIAL_FAILURE");
        assertThat(first.get("failedIds")).isEqualTo(List.of(f.opportunity));
        assertThat(jdbc.queryForObject("SELECT activity_status FROM crm_opportunity WHERE id=?",String.class,f.opportunity)).isEqualTo("ACTIVE");
        long secondVersion=jdbc.queryForObject("SELECT version FROM crm_opportunity WHERE id=?",Long.class,second);
        reset(opportunities);
        doThrow(new IllegalStateException("must not enumerate full candidates on retry")).when(opportunities).activityCandidates();
        var retried=ok(call("POST","/jobs/"+id+"/retry",f.admin,null));
        assertThat(retried.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(retried.path("processed").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM crm_opportunity WHERE id=?",Long.class,second)).isEqualTo(secondVersion);
        assertThat(jdbc.queryForObject("SELECT run_status FROM crm_job_log WHERE id=?",String.class,id)).isEqualTo("PARTIAL_FAILURE");
        assertThat(call("POST","/jobs/"+id+"/retry",f.admin,null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** 两个并发补跑请求最多一个接受，第二个不能再次重试旧失败记录。 */
    @Test void concurrentRetryRejectsStaleSource() throws Exception {
        var result=jobs.execute("opportunity-activity-scan",LocalDate.now(clock),f.admin,null,()->List.of(f.opportunity),id->{throw new IllegalStateException("test failed item");});
        long id=((Number)result.get("id")).longValue();
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->call("POST","/jobs/"+id+"/retry",f.admin,null));
            var b=pool.submit(()->call("POST","/jobs/"+id+"/retry",f.admin,null));
            assertThat(List.of(a.get(30,TimeUnit.SECONDS).getStatusCode(),b.get(30,TimeUnit.SECONDS).getStatusCode())).containsExactlyInAnyOrder(HttpStatus.OK,HttpStatus.CONFLICT);
        } finally {pool.shutdownNow();}
    }

    /** 按稳定事件身份插入独立待办。 */
    long todo(Long user,String biz,Long bizId,String type,LocalDate date) {
        jdbc.update("INSERT INTO crm_todo(todo_type,biz_type,biz_id,occurrence_key,remind_date,recipient_id,title) VALUES(?,?,?,?,?,?,?)",type,biz,bizId,f.prefix,date,user,"隔离测试提醒");
        return jdbc.queryForObject("SELECT id FROM crm_todo WHERE occurrence_key=? AND remind_date=? AND recipient_id=? AND biz_type=?",Long.class,f.prefix,date,user,biz);
    }
    /** 通过真实JWT过滤器调用任务接口。 */
    ResponseEntity<JsonNode> call(String method,String suffix,Long actor,Object body) {
        HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.setBearerAuth(tokens.issue(AuthPrincipal.from(users.findWithTeamById(actor))).value());
        return http.exchange("/api/tasks"+suffix,HttpMethod.valueOf(method),new HttpEntity<>(body,headers),JsonNode.class);
    }
    /** 断言HTTP与统一业务响应均成功。 */
    JsonNode ok(ResponseEntity<JsonNode> response) {assertThat(response.getStatusCode()).withFailMessage("%s",response.getBody()).isEqualTo(HttpStatus.OK);assertThat(response.getBody().path("code").asInt()).isZero();return response.getBody().path("data");}
}
