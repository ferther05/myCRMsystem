/* 文件作用：使用隔离数据验证真实 HTTP、金额事务、审批、换版、冲正和结清闭环。 */
package com.bishe.crm;

import static org.assertj.core.api.Assertions.assertThat;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.auth.security.JwtTokenService;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import com.bishe.crm.module.contract.service.PaymentService;
import com.bishe.crm.module.contract.mapper.PaymentMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class PaymentFlowIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    @Autowired SystemUserMapper users;
    @Autowired PaymentService payments;
    @Autowired PaymentMapper mapper;
    @Autowired Clock businessClock;
    @Autowired com.bishe.crm.module.opportunity.service.OpportunityService opportunities;
    String prefix;
    Long team, sales, manager, admin, peer, customer, opportunity, contract;
    List<Long> actors = new ArrayList<>();
    List<Long> extraOpportunities = new ArrayList<>();

    /** 创建独立团队、账号和1000元签署完成合同，不修改演示账号。 */
    @BeforeEach void setup() {
        prefix = "pay-it-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO sys_team(team_code,team_name) VALUES(?,?)", prefix, prefix);
        team = jdbc.queryForObject("SELECT id FROM sys_team WHERE team_code=?", Long.class, prefix);
        sales = user("sales", "SPECIALIST"); manager = user("manager", "MANAGER");
        admin = user("admin", "ADMIN"); peer = user("peer", "SPECIALIST");
        jdbc.update("INSERT INTO crm_customer(customer_name,normalized_name,owner_id,team_id,customer_status,sea_type) VALUES(?,?,?,?,'PROTECTED','PRIVATE')", prefix, prefix, sales, team);
        customer = jdbc.queryForObject("SELECT id FROM crm_customer WHERE customer_name=?", Long.class, prefix);
        jdbc.update("INSERT INTO crm_opportunity(customer_id,opportunity_name,owner_id,team_id,stage_code,created_by) VALUES(?,?,?,?,'WON',?)", customer, prefix, sales, team, admin);
        opportunity = jdbc.queryForObject("SELECT id FROM crm_opportunity WHERE customer_id=?", Long.class, customer);
        jdbc.update("INSERT INTO crm_contract(contract_no,customer_id,opportunity_id,owner_id,team_id,contract_name,contract_amount,outstanding_amount,contract_status,signing_progress,registration_approved_at,created_by) VALUES(?,?,?,?,?,?,1000,1000,'SIGNED','FILE_CONFIRMED',NOW(),?)", prefix, customer, opportunity, sales, team, prefix, admin);
        contract = jdbc.queryForObject("SELECT id FROM crm_contract WHERE contract_no=?", Long.class, prefix);
    }

    /** 按精确ID删除本测试数据，保留所有用户既有记录。 */
    @AfterEach void cleanup() {
        if (contract != null) {
            jdbc.update("DELETE t FROM crm_todo t JOIN crm_approval a ON t.biz_type='APPROVAL' AND t.biz_id=a.id WHERE a.biz_type='CONTRACT' AND a.biz_id=?", contract);
            jdbc.update("DELETE t FROM crm_todo t JOIN crm_payment_period pp ON t.biz_type='PAYMENT_PERIOD' AND t.biz_id=pp.id JOIN crm_payment_plan p ON p.id=pp.plan_id WHERE p.contract_id=?", contract);
            jdbc.update("UPDATE crm_contract SET current_plan_id=NULL,termination_approval_id=NULL WHERE id=?", contract);
            jdbc.update("DELETE FROM crm_payment_record WHERE contract_id=?", contract);
            jdbc.update("UPDATE crm_payment_period pp JOIN crm_payment_plan p ON p.id=pp.plan_id SET pp.replaced_by_plan_id=NULL WHERE p.contract_id=?", contract);
            jdbc.update("DELETE pp FROM crm_payment_period pp JOIN crm_payment_plan p ON p.id=pp.plan_id WHERE p.contract_id=?", contract);
            jdbc.update("DELETE FROM crm_payment_plan WHERE contract_id=?", contract);
            jdbc.update("DELETE FROM crm_approval WHERE biz_type='CONTRACT' AND biz_id=?", contract);
            jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='CONTRACT' AND biz_id=?", contract);
            jdbc.update("DELETE FROM crm_contract WHERE id=?", contract);
        }
        for (Long id : extraOpportunities) {
            jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='OPPORTUNITY' AND biz_id=?", id);
            jdbc.update("DELETE FROM crm_opportunity_stage_log WHERE opportunity_id=?", id);
            jdbc.update("DELETE FROM crm_opportunity WHERE id=?", id);
        }
        if (opportunity != null) jdbc.update("DELETE FROM crm_opportunity WHERE id=?", opportunity);
        if (customer != null) jdbc.update("DELETE FROM crm_customer WHERE id=?", customer);
        for (Long actor : actors) jdbc.update("DELETE FROM sys_user WHERE id=?", actor);
        if (team != null) jdbc.update("DELETE FROM sys_team WHERE id=?", team);
    }

    /** 首版金额校验、审批、部分到账、幂等、超收、换版承接和最终结清。 */
    @Test void completePaymentAndRevisionLifecycle() {
        assertThat(call("POST", "/payment-plans", sales, plan(999, null)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        long p = createPlan(1000, null); activate(p);
        long period = period(p);
        Map<String,Object> receipt = receipt(period, 200, UUID.randomUUID().toString());
        ok(call("POST", "/payment-records", sales, receipt));
        assertThat(call("POST", "/payment-records", sales, receipt).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call("POST", "/payment-records", sales, receipt(period, 801, UUID.randomUUID().toString())).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertAmount("received_amount", "200"); assertAmount("outstanding_amount", "800");
        assertThat(call("POST", "/payment-plans", sales, plan(800, null)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        long revised = createPlan(800, "剩余款项改为一次收取"); activate(revised);
        assertThat(jdbc.queryForObject("SELECT received_amount FROM crm_payment_period WHERE id=?", BigDecimal.class, period)).isEqualByComparingTo("200");
        assertThat(jdbc.queryForObject("SELECT replaced_by_plan_id FROM crm_payment_period WHERE id=?", Long.class, period)).isEqualTo(revised);
        assertThat(call("POST", "/payment-records", sales, receipt(period, 1, UUID.randomUUID().toString())).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ok(call("POST", "/payment-records", sales, receipt(period(revised), 800, UUID.randomUUID().toString())));
        assertAmount("received_amount", "1000"); assertAmount("outstanding_amount", "0");
        assertThat(jdbc.queryForObject("SELECT contract_status FROM crm_contract WHERE id=?", String.class, contract)).isEqualTo("SETTLED");
        assertThat(jdbc.queryForObject("SELECT customer_status FROM crm_customer WHERE id=?", String.class, customer)).isEqualTo("DEAL");
        assertThat(jdbc.queryForObject("SELECT permanent_deal_at FROM crm_customer WHERE id=?", LocalDateTime.class, customer)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_payment_record WHERE contract_id=?", Integer.class, contract)).isEqualTo(2);
    }

    /** 历史到账冲正后暂停计划，重新审批全额剩余应收后恢复闭环。 */
    @Test void voidHistoricalReceiptRequiresReplanningAndKeepsHistory() {
        long first = createPlan(1000, null); activate(first);
        ok(call("POST", "/payment-records", sales, receipt(period(first), 200, UUID.randomUUID().toString())));
        long revised = createPlan(800, "延期安排"); activate(revised);
        long r = jdbc.queryForObject("SELECT id FROM crm_payment_record WHERE contract_id=?", Long.class, contract);
        assertThat(call("POST", "/payment-records/" + r + "/void", manager, Map.of("version",0,"reason","误录")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ok(call("POST", "/payment-records/" + r + "/void", admin, Map.of("version",0,"reason","误录到账")));
        assertAmount("outstanding_amount", "1000");
        assertThat(jdbc.queryForObject("SELECT approval_status FROM crm_payment_plan WHERE id=?", String.class, revised)).isEqualTo("SUSPENDED");
        assertThat(call("POST", "/payment-records", sales, receipt(period(revised),800,UUID.randomUUID().toString())).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        long repair = createPlan(1000, "历史到账冲正后重编"); activate(repair);
        ok(call("POST", "/payment-records", sales, receipt(period(repair),1000,UUID.randomUUID().toString())));
        assertAmount("received_amount", "1000");
        assertThat(ok(call("GET", "/payments", sales, null)).path("receipts").size()).isEqualTo(2);
    }

    /** 逾期待办每日去重，部分到账不解除，全部收齐自动解除。 */
    @Test void overdueReminderSurvivesPartialReceipt() {
        long p = createPlan(1000, null); activate(p); long period = period(p);
        LocalDate today = LocalDate.now(businessClock);
        for (int i=0;i<2;i++) payments.scanOverduePeriod(contract,period,p,sales,team,today,prefix);
        assertThat(pendingTodos(period)).isEqualTo(2);
        ok(call("POST", "/payment-records", sales, receipt(period,200,UUID.randomUUID().toString())));
        assertThat(pendingTodos(period)).isEqualTo(2);
        ok(call("POST", "/payment-records", sales, receipt(period,800,UUID.randomUUID().toString())));
        assertThat(pendingTodos(period)).isZero();
    }

    /** 无应收终止需非申请人审核，批准后禁止再收款。 */
    @Test void terminationRequiresIndependentReview() {
        ok(call("POST", "/termination", manager, Map.of("reason","业务取消","noReceivableExplanation","双方确认未产生应收")));
        assertThat(call("POST", "/termination/review", manager, Map.of("version",0,"pass",true,"opinion","核验")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ok(call("POST", "/termination/review", admin, Map.of("version",0,"pass",true,"opinion","确认无应收")));
        assertThat(jdbc.queryForObject("SELECT contract_status FROM crm_contract WHERE id=?", String.class, contract)).isEqualTo("TERMINATED");
        assertAmount("outstanding_amount", "0");
        assertThat(call("POST", "/payment-plans", sales, plan(1000,null)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** 无签署最终核验不能结清，核验完成后自动刷新；冲正重新打开合同。 */
    @Test void settlementRequiresSigningAndCanBeReopenedByVoid() {
        jdbc.update("UPDATE crm_contract SET signing_progress='FILE_REVIEW' WHERE id=?",contract);
        long p=createPlan(1000,null); activate(p);
        payments.scanOverduePeriod(contract,period(p),p,sales,team,LocalDate.now(businessClock),prefix);
        assertThat(pendingTodos(period(p))).isEqualTo(2);
        ok(call("POST","/payment-records",sales,receipt(period(p),1000,UUID.randomUUID().toString())));
        assertThat(jdbc.queryForObject("SELECT contract_status FROM crm_contract WHERE id=?",String.class,contract)).isEqualTo("SIGNED");
        jdbc.update("UPDATE crm_contract SET signing_progress='FILE_CONFIRMED' WHERE id=?",contract);
        payments.refreshAfterSigning(contract);
        assertThat(jdbc.queryForObject("SELECT contract_status FROM crm_contract WHERE id=?",String.class,contract)).isEqualTo("SETTLED");
        long r=jdbc.queryForObject("SELECT id FROM crm_payment_record WHERE contract_id=?",Long.class,contract);
        ok(call("POST","/payment-records/"+r+"/void",admin,Map.of("version",0,"reason","误录")));
        assertAmount("outstanding_amount","1000");
        assertThat(jdbc.queryForObject("SELECT contract_status FROM crm_contract WHERE id=?",String.class,contract)).isEqualTo("PERFORMING");
        assertThat(pendingTodos(period(p))).isEqualTo(2);
    }

    /** 并发到账只能有一笔成功，另一笔因超收回滚。 */
    @Test void concurrentReceiptsCannotOvercollect() throws Exception {
        long p=createPlan(1000,null); activate(p); long period=period(p);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->call("POST","/payment-records",sales,receipt(period,700,UUID.randomUUID().toString())));
            var b=pool.submit(()->call("POST","/payment-records",sales,receipt(period,700,UUID.randomUUID().toString())));
            assertThat(List.of(a.get(30,TimeUnit.SECONDS).getStatusCode(),b.get(30,TimeUnit.SECONDS).getStatusCode())).containsExactlyInAnyOrder(HttpStatus.OK,HttpStatus.BAD_REQUEST);
            assertAmount("received_amount","700");
        } finally { pool.shutdownNow(); }
    }

    /** 非负责人不能登记或修改计划；审批人不能自审。 */
    @Test void scopeAndSelfApprovalAreRejected() {
        assertThat(call("GET","/payments",peer,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call("POST","/payment-plans",manager,plan(1000,null)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        jdbc.update("UPDATE crm_contract SET owner_id=? WHERE id=?",manager,contract);
        long p=ok(call("POST","/payment-plans",manager,plan(1000,null))).asLong();
        ok(call("POST","/payment-plans/"+p+"/submit",manager,Map.of("version",version(p))));
        assertThat(call("POST","/payment-plans/"+p+"/review",manager,Map.of("version",version(p),"pass",true,"opinion","自审")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** 两期独立核销；今天到期尚不逾期，不能用第一期到账代替整份合同结清。 */
    @Test void multiplePeriodsAndDueDateBoundary() {
        Map<String,Object> body = plan(1000, null);
        body.put("periods", List.of(
            Map.of("plannedAmount",400,"dueDate",LocalDate.now(businessClock).toString(),"triggerNode","SIGNING"),
            Map.of("plannedAmount",600,"dueDate",LocalDate.now(businessClock).plusDays(7).toString(),"triggerNode","DELIVERY")));
        long p = ok(call("POST","/payment-plans",sales,body)).asLong(); activate(p);
        List<Long> periods = jdbc.queryForList("SELECT id FROM crm_payment_period WHERE plan_id=? ORDER BY period_no",Long.class,p);
        payments.scanOverduePeriod(contract,periods.get(0),p,sales,team,LocalDate.now(businessClock),prefix);
        assertThat(pendingTodos(periods.get(0))).isZero();
        ok(call("POST","/payment-records",sales,receipt(periods.get(0),400,UUID.randomUUID().toString())));
        assertAmount("outstanding_amount","600");
        assertThat(ok(call("GET","/payments",sales,null)).path("paymentStatus").asText()).isEqualTo("PARTIAL");
        ok(call("POST","/payment-records",sales,receipt(periods.get(1),600,UUID.randomUUID().toString())));
        assertThat(ok(call("GET","/payments",sales,null)).path("paymentStatus").asText()).isEqualTo("SETTLED");
    }

    /** 审批退回允许修改重提，过期版本必须拒绝。 */
    @Test void returnedPlanCanBeEditedAndResubmitted() {
        long p=createPlan(1000,null);
        ok(call("POST","/payment-plans/"+p+"/submit",sales,Map.of("version",version(p))));
        ok(call("POST","/payment-plans/"+p+"/review",manager,Map.of("version",version(p),"pass",false,"opinion","调整日期")));
        assertThat(call("PUT","/payment-plans/"+p,sales,plan(1000,null)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<String,Object> body=plan(1000,null);body.put("version",version(p));
        ok(call("PUT","/payment-plans/"+p,sales,body));activate(p);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_payment_plan WHERE contract_id=? AND is_current=1",Integer.class,contract)).isEqualTo(1);
    }

    /** 换版待审期间又收到款，旧金额的审批必须整体回滚，不误替换生效计划。 */
    @Test void approvalRechecksBalanceAndRollsBack() {
        long p=createPlan(1000,null);activate(p);
        long revised=createPlan(1000,"重新安排");
        ok(call("POST","/payment-plans/"+revised+"/submit",sales,Map.of("version",version(revised))));
        ok(call("POST","/payment-records",sales,receipt(period(p),200,UUID.randomUUID().toString())));
        assertThat(call("POST","/payment-plans/"+revised+"/review",manager,Map.of("version",version(revised),"pass",true,"opinion","核对")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT approval_status FROM crm_payment_plan WHERE id=?",String.class,revised)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT current_plan_id FROM crm_contract WHERE id=?",Long.class,contract)).isEqualTo(p);
        assertThat(jdbc.queryForObject("SELECT approval_status FROM crm_approval WHERE id=(SELECT approval_id FROM crm_payment_plan WHERE id=?)",String.class,revised)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_operation_log WHERE biz_type='CONTRACT' AND biz_id=? AND action_code='PAYMENT_PLAN_REVIEWED'",Integer.class,contract)).isEqualTo(1);
    }

    /** 其他未结束商机阻止客户最终成交，合同自身可正常结清。 */
    @Test void unfinishedOpportunityPreventsFinalCustomerDeal() {
        jdbc.update("INSERT INTO crm_opportunity(customer_id,opportunity_name,owner_id,team_id,stage_code,created_by) VALUES(?,?,?,?,'NEW',?)",customer,prefix+"-open",sales,team,admin);
        extraOpportunities.add(jdbc.queryForObject("SELECT id FROM crm_opportunity WHERE opportunity_name=?",Long.class,prefix+"-open"));
        long p=createPlan(1000,null);activate(p);
        ok(call("POST","/payment-records",sales,receipt(period(p),1000,UUID.randomUUID().toString())));
        assertThat(jdbc.queryForObject("SELECT customer_status FROM crm_customer WHERE id=?",String.class,customer)).isEqualTo("PROTECTED");
        assertThat(jdbc.queryForObject("SELECT permanent_deal_at FROM crm_customer WHERE id=?",LocalDateTime.class,customer)).isNull();
        opportunities.changeStage(AuthPrincipal.from(users.findWithTeamById(admin)), extraOpportunities.get(0),
            new com.bishe.crm.module.opportunity.dto.OpportunityRequests.StageAction(0L,"CLOSED","其他项目已关闭"));
        assertThat(jdbc.queryForObject("SELECT customer_status FROM crm_customer WHERE id=?",String.class,customer)).isEqualTo("DEAL");
    }

    /** 通过业务主键取出测试用户ID。 */
    Long user(String name,String role) {
        jdbc.update("INSERT INTO sys_user(username,password_hash,real_name,role_code,team_id,account_status,must_change_password) VALUES(?,'unused-test-hash',?,?,?,'ACTIVE',0)",prefix+name,prefix+name,role,role.equals("ADMIN")?null:team);
        Long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,prefix+name);actors.add(id);return id;
    }
    /** 构造一期计划，过去日期便于验证逾期边界。 */
    Map<String,Object> plan(int amount,String description) {
        Map<String,Object> body=new HashMap<>();body.put("version",0);body.put("changeDescription",description);
        body.put("periods",List.of(Map.of("plannedAmount",amount,"dueDate",LocalDate.now(businessClock).minusDays(1).toString(),"triggerNode","OTHER")));return body;
    }
    /** 保存首版或换版。 */
    long createPlan(int amount,String description) {return ok(call("POST","/payment-plans",sales,plan(amount,description))).asLong();}
    /** 提交并由独立经理审批生效。 */
    void activate(long p) {
        ok(call("POST","/payment-plans/"+p+"/submit",sales,Map.of("version",version(p))));
        ok(call("POST","/payment-plans/"+p+"/review",manager,Map.of("version",version(p),"pass",true,"opinion","已核对")));
    }
    /** 获取计划乐观锁版本。 */
    long version(long p) {return jdbc.queryForObject("SELECT version FROM crm_payment_plan WHERE id=?",Long.class,p);}
    /** 获取计划第一期。 */
    long period(long p) {return jdbc.queryForObject("SELECT id FROM crm_payment_period WHERE plan_id=? ORDER BY period_no LIMIT 1",Long.class,p);}
    /** 构造到账请求，部分到账说明齐全。 */
    Map<String,Object> receipt(long period,int amount,String key) {return Map.of("periodId",period,"amount",amount,"receivedAt",LocalDateTime.now(businessClock).minusMinutes(1).withNano(0).toString(),"paymentMethod","BANK_TRANSFER","differenceDescription","剩余按约定支付","idempotencyKey",key);}
    /** 读取本期仍待办的提醒数。 */
    int pendingTodos(long period) {return jdbc.queryForObject("SELECT COUNT(*) FROM crm_todo WHERE biz_type='PAYMENT_PERIOD' AND biz_id=? AND todo_status='PENDING'",Integer.class,period);}
    /** 核对合同数据库金额。 */
    void assertAmount(String field,String amount) {assertThat(jdbc.queryForObject("SELECT "+field+" FROM crm_contract WHERE id=?",BigDecimal.class,contract)).isEqualByComparingTo(amount);}
    /** 通过真实JWT过滤链调用HTTP接口。 */
    ResponseEntity<JsonNode> call(String method,String suffix,Long actor,Object body) {
        HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokens.issue(AuthPrincipal.from(users.findWithTeamById(actor))).value());
        return http.exchange("/api/contracts/"+contract+suffix,HttpMethod.valueOf(method),new HttpEntity<>(body,headers),JsonNode.class);
    }
    /** 断言成功，失败时显示真实响应便于定位。 */
    JsonNode ok(ResponseEntity<JsonNode> response) {assertThat(response.getStatusCode()).withFailMessage("%s",response.getBody()).isEqualTo(HttpStatus.OK);assertThat(response.getBody().path("code").asInt()).isZero();return response.getBody().path("data");}
}
