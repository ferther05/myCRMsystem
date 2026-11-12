/* 文件作用：隔离真实MySQL/JWT验证工作台口径、权限、分页和当前状态定位。 */
package com.bishe.crm;

import static org.assertj.core.api.Assertions.assertThat;
import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import com.bishe.crm.module.contract.service.PaymentService;
import com.bishe.crm.module.contract.mapper.PaymentMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class WorkbenchIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    @Autowired SystemUserMapper users;
    @Autowired PaymentService payments;
    @Autowired PaymentMapper mapper;
    @Autowired Clock clock;
    @Autowired com.bishe.crm.module.customer.mapper.CustomerMapper customers;
    @Autowired com.bishe.crm.module.customer.service.CustomerRecycleService recycle;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    PaymentFlowIT f,g;

    @BeforeEach void setup() { f=fixture();g=fixture(); }
    private PaymentFlowIT fixture() {
        var v=new PaymentFlowIT();v.http=http;v.jdbc=jdbc;v.tokens=tokens;v.users=users;v.payments=payments;v.mapper=mapper;v.businessClock=clock;v.setup();return v;
    }
    /** 精确删除两套fixture及其本次衍生记录，不改演示数据。 */
    @AfterEach void cleanup() {
        // 跨fixture协作先解除双方外键，再依次删除账号和团队。
        for(var v:Arrays.asList(f,g)) if(v!=null)
            jdbc.update("DELETE FROM crm_opportunity_collaborator WHERE opportunity_id=?",v.opportunity);
        for(var v:Arrays.asList(f,g)) if(v!=null) {
            jdbc.update("DELETE FROM crm_follow_record WHERE customer_id=?",v.customer);
            jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=?",v.customer);
            jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='OPPORTUNITY' AND biz_id=?",v.opportunity);
            jdbc.update("DELETE FROM crm_opportunity_stage_log WHERE opportunity_id=?",v.opportunity);
            jdbc.update("DELETE FROM crm_todo WHERE recipient_id IN (?,?,?,?)",v.sales,v.manager,v.admin,v.peer);
            jdbc.update("DELETE FROM crm_job_log WHERE triggered_by=?",v.admin);
            jdbc.update("DELETE FROM crm_approval WHERE biz_type='CUSTOMER' AND biz_id=?",v.customer);
            jdbc.update("DELETE FROM crm_opportunity_collaborator WHERE opportunity_id=?",v.opportunity);
            v.cleanup();
        }
    }
    @Test void allRoleCategoriesAndParameterGuards() {
        for(long actor:List.of(f.sales,f.manager,f.admin)) {
            var overview=ok(get(actor,"/overview"));
            assertThat(overview.path("metrics").size()).isEqualTo(4);
            for(var category:overview.path("categories")) {
                var rows=items(actor,category.path("key").asText(),1);
                assertThat(rows.path("total").asLong()).isEqualTo(category.path("count").asLong());
            }
        }
        assertThat(get(f.sales,"/items?category=JOB_FAILURE").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(f.manager,"/items?category=ACCOUNTS").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        for(String query:List.of("category=UNKNOWN","category=REVIEW&page=0","category=REVIEW&size=20"))
            assertThat(get(f.manager,"/items?"+query).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.getForEntity("/api/workbench/overview",JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    @Test void administratorResourceAnalyticsIsScopedAndKeepsTeamDistributionSeparate() {
        var response=http.exchange("/api/analytics/admin-resources",HttpMethod.GET,
            new HttpEntity<>(headers(f.admin)),JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var result=response.getBody().path("data");
        assertThat(result.path("activeAccounts").asLong()).isGreaterThan(0);
        assertThat(result.path("publicCustomers").asLong()).isGreaterThanOrEqualTo(result.path("unassignedPublicCustomers").asLong());
        assertThat(result.path("teams").isArray()).isTrue();
        assertThat(result.path("teams").get(0).has("activeMembers")).isTrue();
        for(long actor:List.of(f.manager,f.sales)) {
            var denied=http.exchange("/api/analytics/admin-resources",HttpMethod.GET,
                new HttpEntity<>(headers(actor)),JsonNode.class);
            assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(denied.getBody().path("code").asInt()).isEqualTo(40300);
        }
    }
    @Test void customerAndOpportunityListExposeCreationTimeForRecentBadges() {
        var customer=http.exchange("/api/customers?page=1&size=10",HttpMethod.GET,
            new HttpEntity<>(headers(f.sales)),JsonNode.class);
        assertThat(customer.getStatusCode()).isEqualTo(HttpStatus.OK);
        var customerRows=customer.getBody().path("data").path("list");
        var customerRow=java.util.stream.StreamSupport.stream(customerRows.spliterator(),false)
            .filter(row->row.path("id").asLong()==f.customer).findFirst().orElseThrow();
        assertThat(customerRow.path("createdAt").isTextual()).isTrue();

        var opportunity=http.exchange("/api/opportunities?page=1&size=10",HttpMethod.GET,
            new HttpEntity<>(headers(f.sales)),JsonNode.class);
        assertThat(opportunity.getStatusCode()).isEqualTo(HttpStatus.OK);
        var opportunityRows=opportunity.getBody().path("data").path("list");
        var opportunityRow=java.util.stream.StreamSupport.stream(opportunityRows.spliterator(),false)
            .filter(row->row.path("id").asLong()==f.opportunity).findFirst().orElseThrow();
        assertThat(opportunityRow.path("createdAt").isTextual()).isTrue();
    }
    @Test void followQueueUsesCurrentStageScopeNoSelfAndStablePages() {
        stage(f);stage(g);
        for(int i=0;i<12;i++) follow(f,f.sales,"PENDING",3,"good-"+i);
        follow(f,f.manager,"PENDING",3,"self");
        follow(f,f.sales,"CONFIRMED",3,"done");
        follow(f,f.sales,"PENDING",2,"old-stage");
        follow(g,g.sales,"PENDING",3,"other-team");
        var first=items(f.manager,"FOLLOW_REVIEW",1);var second=items(f.manager,"FOLLOW_REVIEW",2);
        assertThat(first.path("total").asLong()).isEqualTo(12);
        assertThat(first.path("list").size()).isEqualTo(10);assertThat(second.path("list").size()).isEqualTo(2);
        Set<String> keys=new HashSet<>();for(var row:first.path("list")) keys.add(row.path("key").asText());
        for(var row:second.path("list")) assertThat(keys.add(row.path("key").asText())).isTrue();
        assertThat(first.path("list").get(0).path("target").asText()).contains("id="+f.opportunity,"section=follow","followRecordId=");
        // 客户归属周期更新后，旧周期跟进不能出现在待核验首页。
        jdbc.update("UPDATE crm_customer SET ownership_started_at=DATE_ADD(NOW(),INTERVAL 1 DAY) WHERE id=?",f.customer);
        assertThat(items(f.manager,"FOLLOW_REVIEW",1).path("total").asLong()).isZero();
    }
    @Test void currentContractReviewAndReturnDoNotExposeOtherTeamOrSelf() {
        jdbc.update("UPDATE crm_contract SET contract_status='PENDING_REVIEW',signing_progress='DRAFT_REVIEW' WHERE id IN (?,?)",f.contract,g.contract);
        var result=items(f.manager,"CONTRACT_REVIEW",1);
        assertThat(result.path("total").asLong()).isEqualTo(1);
        assertThat(result.path("list").get(0).path("target").asText()).contains("contractId="+f.contract,"section=progress");
        jdbc.update("UPDATE crm_contract SET owner_id=? WHERE id=?",f.manager,f.contract);
        assertThat(items(f.manager,"CONTRACT_REVIEW",1).path("total").asLong()).isZero();
        jdbc.update("UPDATE crm_contract SET owner_id=?,contract_status='RETURNED',signing_progress='SIGNED_UPLOAD' WHERE id=?",f.sales,f.contract);
        assertThat(items(f.sales,"RETURNED",1).path("list").get(0).path("target").asText()).contains("section=progress");
        jdbc.update("UPDATE crm_contract SET signing_progress='EXCEPTION' WHERE id=?",f.contract);
        assertThat(items(f.sales,"RETURNED",1).path("total").asLong()).isZero();
    }
    @Test void personalAlreadyReadPendingAndCollaborationRemainSeparateFromCustomerAccess() {
        todo(f.sales,f.customer,"PENDING",true);todo(f.sales,f.customer,"RESOLVED",false);
        todo(f.sales,g.customer,"PENDING",false);todo(f.peer,f.customer,"PENDING",false);
        assertThat(items(f.sales,"FOLLOW_DUE",1).path("total").asLong()).isEqualTo(1);
        stage(g);
        jdbc.update("INSERT INTO crm_opportunity_collaborator(opportunity_id,user_id) VALUES(?,?)",g.opportunity,f.sales);
        assertThat(items(f.sales,"OPEN",1).path("total").asLong()).isEqualTo(1);
        assertThat(items(f.sales,"FOLLOW_DUE",1).path("total").asLong()).isEqualTo(1);
        jdbc.update("UPDATE crm_opportunity_collaborator SET collaborator_status='LEFT',left_at=NOW() WHERE opportunity_id=?",g.opportunity);
        assertThat(items(f.sales,"OPEN",1).path("total").asLong()).isZero();
    }
    @Test void approvalAndOverdueReflectActualPaymentLifecycle() {
        long p=f.createPlan(1000,null);
        f.ok(f.call("POST","/payment-plans/"+p+"/submit",f.sales,Map.of("version",0)));
        assertThat(items(f.manager,"APPROVAL",1).path("total").asLong()).isEqualTo(1);
        jdbc.update("UPDATE crm_payment_plan SET submitted_by=? WHERE id=?",f.manager,p);
        assertThat(items(f.manager,"APPROVAL",1).path("total").asLong()).isZero();
        jdbc.update("UPDATE crm_payment_plan SET submitted_by=?,approval_status='ACTIVE',is_current=1 WHERE id=?",f.sales,p);
        jdbc.update("UPDATE crm_payment_period SET due_date=DATE_SUB(CURDATE(),INTERVAL 1 DAY) WHERE plan_id=?",p);
        var result=items(f.manager,"OVERDUE",1);
        assertThat(result.path("total").asLong()).isEqualTo(1);
        assertThat(result.path("list").get(0).path("amount").decimalValue()).isEqualByComparingTo("1000");
        jdbc.update("UPDATE crm_payment_period SET received_amount=planned_amount WHERE plan_id=?",p);
        assertThat(items(f.manager,"OVERDUE",1).path("total").asLong()).isZero();
    }
    @Test void latestJobAttemptOnlyAndRealReviewRemovesQueueItem() {
        String logical=f.prefix+":workbench";
        jdbc.update("INSERT INTO crm_job_log(job_name,business_date,logical_run_id,attempt_no,run_status,started_at,triggered_by) VALUES('business-reminder-scan',CURDATE(),?,1,'FAILED',NOW(),?)",logical,f.admin);
        long before=items(f.admin,"JOB_FAILURE",1).path("total").asLong();
        jdbc.update("INSERT INTO crm_job_log(job_name,business_date,logical_run_id,attempt_no,run_status,started_at,triggered_by) VALUES('business-reminder-scan',CURDATE(),?,2,'SUCCESS',NOW(),?)",logical,f.admin);
        assertThat(items(f.admin,"JOB_FAILURE",1).path("total").asLong()).isEqualTo(before-1);
        stage(f);long id=follow(f,f.sales,"PENDING",3,"review-me");
        long version=jdbc.queryForObject("SELECT version FROM crm_customer WHERE id=?",Long.class,f.customer);
        var headers=headers(f.manager);
        var response=http.exchange("/api/customers/"+f.customer+"/follow-records/"+id+"/review",HttpMethod.PATCH,new HttpEntity<>(Map.of("version",version,"reviewStatus","CONFIRMED","reviewReason","工作台隔离验收"),headers),JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(items(f.manager,"FOLLOW_REVIEW",1).path("total").asLong()).isZero();
    }
    @Test void recycleAppealsExcludeStaleOwnershipAndFollowCycles() throws Exception {
        var c=customers.selectById(f.customer);
        String cycle=recycle.rule(c,LocalDateTime.now(clock)).cycleKey();
        String extra=json.writeValueAsString(Map.of("ownershipSeq",c.getOwnershipSeq(),"cycleKey",cycle));
        jdbc.update("INSERT INTO crm_approval(approval_type,biz_type,biz_id,applicant_id,reason,extra_json) VALUES('RECYCLE_APPEAL','CUSTOMER',?,?,'隔离延期申请',?)",f.customer,f.sales,extra);
        assertThat(items(f.manager,"APPROVAL",1).path("total").asLong()).isEqualTo(1);
        assertThat(items(f.manager,"APPROVAL",1).path("list").get(0).path("target").asText()).contains("section=recycle");
        jdbc.update("UPDATE crm_customer SET last_effective_follow_at=NOW() WHERE id=?",f.customer);
        assertThat(items(f.manager,"APPROVAL",1).path("total").asLong()).isZero();
        c=customers.selectById(f.customer);cycle=recycle.rule(c,LocalDateTime.now(clock)).cycleKey();
        extra=json.writeValueAsString(Map.of("ownershipSeq",c.getOwnershipSeq(),"cycleKey",cycle));
        jdbc.update("UPDATE crm_approval SET extra_json=? WHERE biz_type='CUSTOMER' AND biz_id=?",extra,f.customer);
        assertThat(items(f.manager,"APPROVAL",1).path("total").asLong()).isEqualTo(1);
        jdbc.update("UPDATE crm_customer SET ownership_seq=ownership_seq+1 WHERE id=?",f.customer);
        assertThat(items(f.manager,"APPROVAL",1).path("total").asLong()).isZero();
    }
    @Test void terminationReviewAndCloseDecisionUseTheirActualRules() {
        jdbc.update("INSERT INTO crm_approval(approval_type,biz_type,biz_id,applicant_id,reason) VALUES('CONTRACT_TERMINATION','CONTRACT',?,?,'隔离终止申请')",f.contract,f.sales);
        assertThat(items(f.manager,"APPROVAL",1).path("total").asLong()).isEqualTo(1);
        jdbc.update("UPDATE crm_approval SET applicant_id=? WHERE biz_type='CONTRACT' AND biz_id=?",f.manager,f.contract);
        assertThat(items(f.manager,"APPROVAL",1).path("total").asLong()).isZero();
        stage(f);
        jdbc.update("UPDATE crm_opportunity SET activity_status='CLOSE_PENDING',close_pending_at=NOW() WHERE id=?",f.opportunity);
        var item=items(f.manager,"APPROVAL",1).path("list").get(0);
        assertThat(item.path("type").asText()).isEqualTo("OPP_CLOSE_PENDING");
        assertThat(item.path("target").asText()).contains("section=records");
        jdbc.update("UPDATE crm_opportunity SET activity_status='ACTIVE' WHERE id=?",f.opportunity);
        assertThat(items(f.manager,"APPROVAL",1).path("total").asLong()).isZero();
    }
    private void stage(PaymentFlowIT v) { jdbc.update("UPDATE crm_opportunity SET stage_code='FOLLOWING',stage_seq=3,activity_status='ACTIVE' WHERE id=?",v.opportunity); }
    private long follow(PaymentFlowIT v,long author,String status,long seq,String suffix) {
        String content=v.prefix+suffix;
        jdbc.update("INSERT INTO crm_follow_record(customer_id,opportunity_id,opportunity_stage_code,opportunity_stage_seq,follow_user_id,record_type,content,is_effective,review_status) VALUES(?,?,'FOLLOWING',?,?,'FOLLOW',?,1,?)",v.customer,v.opportunity,seq,author,content,status);
        return jdbc.queryForObject("SELECT id FROM crm_follow_record WHERE content=?",Long.class,content);
    }
    private void todo(long actor,long customer,String status,boolean read) {
        jdbc.update("INSERT INTO crm_todo(todo_type,biz_type,biz_id,occurrence_key,remind_date,recipient_id,title,todo_status,read_at) VALUES('FOLLOW_DUE','CUSTOMER',?,?,CURDATE(),?,'隔离工作台提醒',?,?)",customer,UUID.randomUUID().toString(),actor,status,read?LocalDateTime.now(clock):null);
    }
    private JsonNode items(long actor,String category,int page) { return ok(get(actor,"/items?category="+category+"&page="+page+"&size=10")); }
    private HttpHeaders headers(long actor) { var h=new HttpHeaders();h.setBearerAuth(tokens.issue(AuthPrincipal.from(users.findWithTeamById(actor))).value());return h; }
    private ResponseEntity<JsonNode> get(long actor,String path) { return http.exchange("/api/workbench"+path,HttpMethod.GET,new HttpEntity<>(headers(actor)),JsonNode.class); }
    private JsonNode ok(ResponseEntity<JsonNode> result) { assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(result.getBody().path("code").asInt()).isZero();return result.getBody().path("data"); }
}
