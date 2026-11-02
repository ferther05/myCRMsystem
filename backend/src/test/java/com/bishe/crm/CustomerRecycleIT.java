/* 文件作用：用隔离数据验证公海回收时间、保护、延期、事务与重新领取权限。 */
package com.bishe.crm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.customer.mapper.*;
import com.bishe.crm.module.customer.service.CustomerRecycleService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class CustomerRecycleIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired SystemUserMapper users;
    @Autowired JwtTokenService tokens;
    @Autowired CustomerRecycleService recycle;
    @MockitoSpyBean RecycleMapper mapper;
    @MockitoSpyBean CustomerMapper customers;
    @MockitoSpyBean Clock clock;
    PaymentFlowIT f;
    LocalDateTime at=LocalDateTime.of(2026,11,1,12,0);
    String originalSetting;
    List<Long> extraCustomers=new ArrayList<>();

    /** 只让扫描器看到隔离客户，绝不扫描演示客户。 */
    @BeforeEach void setup() {
        tick(at);f=new PaymentFlowIT();f.http=http;f.jdbc=jdbc;f.tokens=tokens;f.users=users;f.businessClock=clock;f.setup();
        originalSetting=jdbc.queryForObject("SELECT config_value FROM sys_config WHERE config_group='crm.recycle' AND config_key='enabled'",String.class);
        jdbc.update("UPDATE sys_config SET config_value='false' WHERE config_group='crm.recycle' AND config_key='enabled'");
        jdbc.update("UPDATE crm_opportunity SET stage_code='CLOSED' WHERE id=?",f.opportunity);
        jdbc.update("UPDATE crm_contract SET contract_status='VOID',outstanding_amount=0 WHERE id=?",f.contract);
        jdbc.update("UPDATE crm_customer SET customer_status='NEW',ownership_started_at=?,first_contact_deadline_at=?,last_effective_follow_at=NULL WHERE id=?",at.minusHours(73),at.minusHours(1),f.customer);
        doReturn(List.of(f.customer)).when(mapper).candidates();
    }
    /** 按精确客户、业务与测试身份清理，并恢复原全局开关。 */
    @AfterEach void cleanup() {
        reset(mapper,customers,clock);
        for(Long id:extraCustomers) {jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=?",id);jdbc.update("DELETE FROM crm_customer WHERE id=?",id);}
        jdbc.update("UPDATE sys_config SET config_value=? WHERE config_group='crm.recycle' AND config_key='enabled'",originalSetting);
        jdbc.update("DELETE l FROM crm_operation_log l JOIN crm_todo t ON l.biz_type='TODO' AND l.biz_id=t.id WHERE t.biz_type='CUSTOMER' AND t.biz_id=?",f.customer);
        jdbc.update("DELETE t FROM crm_todo t JOIN crm_approval a ON t.biz_type='APPROVAL' AND t.biz_id=a.id WHERE a.biz_type='CUSTOMER' AND a.biz_id=?",f.customer);
        jdbc.update("DELETE FROM crm_todo WHERE biz_type='CUSTOMER' AND biz_id=?",f.customer);
        jdbc.update("DELETE FROM crm_approval WHERE biz_type='CUSTOMER' AND biz_id=?",f.customer);
        jdbc.update("DELETE FROM crm_follow_record WHERE customer_id=?",f.customer);
        jdbc.update("DELETE FROM crm_contact WHERE customer_id=?",f.customer);
        jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=?",f.customer);
        for(Long a:f.actors) {jdbc.update("DELETE FROM crm_operation_log WHERE operator_id=?",a);jdbc.update("DELETE FROM crm_job_log WHERE triggered_by=?",a);}
        f.cleanup();
    }

    /** 首联48/72小时、常规27/30天按时长，边界前一毫秒不提前。 */
    @Test void exactTimeBoundariesAndNoWarningGate() {
        LocalDateTime base=at.minusHours(48);
        jdbc.update("UPDATE crm_customer SET ownership_started_at=?,first_contact_deadline_at=? WHERE id=?",base,base.plusHours(72),f.customer);
        assertThat(recycle.rule(customers.active(f.customer),at.minusNanos(1_000_000)).decision()).isEqualTo("WAITING");
        assertThat(recycle.rule(customers.active(f.customer),at).decision()).isEqualTo("NEED_WARNING");
        warn();tick(base.plusHours(72));
        assertThat(rule().decision()).isEqualTo("ELIGIBLE");
        jdbc.update("UPDATE crm_customer SET last_effective_follow_at=?,ownership_started_at=?,recycle_warning_key=NULL,recycle_warned_at=NULL WHERE id=?",at.minusDays(27),at.minusDays(60),f.customer);tick(at);
        assertThat(recycle.rule(customers.active(f.customer),at.minusNanos(1_000_000)).decision()).isEqualTo("WAITING");
        assertThat(rule().decision()).isEqualTo("NEED_WARNING");warn();tick(at.plusDays(3));assertThat(rule().decision()).isEqualTo("ELIGIBLE");
    }

    /** 关闭开关时扫描无业务写入，人工发预警必须管理员明确操作且有版本。 */
    @Test void disabledScanIsReadOnlyAndWarningsAreIndependent() {
        Long v=version();scan();assertThat(version()).isEqualTo(v);assertThat(todoCount()).isZero();
        assertThat(call("POST","/recycle/customers/"+f.customer+"/warning",f.sales,Map.of("version",v)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        warn();assertThat(todoCount()).isEqualTo(2); // 本人和团队经理，无其他专员
        assertThat(call("POST","/recycle/customers/"+f.customer+"/warning",f.admin,Map.of("version",v)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rule().deadlineAt()).isEqualTo(at.plusHours(24));
        assertThat(call("GET","/recycle/customers/"+f.customer,f.peer,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** 回收维护持有量、原负责人优先权，历史业务不删除；重新领取重置首联周期。 */
    @Test void recycleAndClaimPreserveHistoryAndRevokeOldAccess() {
        warn();tick(at.plusHours(24));enable(true);scan();
        var c=customers.active(f.customer);assertThat(c.getSeaType()).isEqualTo("PUBLIC");assertThat(c.getOwnerId()).isNull();assertThat(c.getPreviousOwnerId()).isEqualTo(f.sales);
        assertThat(jdbc.queryForObject("SELECT current_hold_count FROM sys_user WHERE id=?",Integer.class,f.sales)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_opportunity WHERE id=?",Integer.class,f.opportunity)).isEqualTo(1);
        assertThat(call("GET","/opportunities/"+f.opportunity,f.sales,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call("GET","/contracts/"+f.contract,f.sales,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(todoPending()).isZero();
        ok(call("POST","/customers/"+f.customer+"/claim?version="+version(),f.peer,null));
        assertThat(customers.active(f.customer).getLastEffectiveFollowAt()).isNull();
        assertThat(rule().deadlineAt()).isEqualTo(at.plusHours(24+72));
        ok(call("GET","/opportunities/"+f.opportunity,f.peer,null));
        assertThat(call("GET","/opportunities/"+f.opportunity,f.sales,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** 每道业务保护都在实际执行前重新检查，不凭旧预览回收。 */
    @Test void protectionGatesAreRechecked() {
        warn();tick(at.plusDays(4));enable(true);
        jdbc.update("UPDATE crm_customer SET permanent_deal_at=? WHERE id=?",at,f.customer);scan();assertPrivate();
        jdbc.update("UPDATE crm_customer SET permanent_deal_at=NULL WHERE id=?",f.customer);
        jdbc.update("UPDATE crm_contract SET contract_status='SIGNED',outstanding_amount=1000 WHERE id=?",f.contract);scan();assertPrivate();
        jdbc.update("UPDATE crm_contract SET contract_status='VOID',outstanding_amount=0 WHERE id=?",f.contract);
        jdbc.update("UPDATE crm_opportunity SET stage_code='FOLLOWING',activity_status='CLOSE_PENDING' WHERE id=?",f.opportunity);scan();assertPrivate();
        jdbc.update("UPDATE crm_opportunity SET stage_code='CLOSED' WHERE id=?",f.opportunity);
        jdbc.update("UPDATE crm_customer SET freeze_type='HANDOVER',freeze_until=? WHERE id=?",at.plusDays(8),f.customer);scan();assertPrivate();
        jdbc.update("UPDATE crm_customer SET freeze_type=NULL,freeze_until=NULL WHERE id=?",f.customer);scan();assertThat(customers.active(f.customer).getSeaType()).isEqualTo("PUBLIC");
    }

    /** 延期由其他管理者批准，按较晚者加7天；同季度不能重复申请。 */
    @Test void appealReviewExtendsAndQuarterLimitApplies() {
        warn();long a=appeal();enable(true);tick(at.plusDays(2));scan();assertPrivate();
        assertThat(call("POST","/recycle/appeals/"+a+"/review",f.sales,reviewBody(true)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ok(call("POST","/recycle/appeals/"+a+"/review",f.manager,reviewBody(true)));
        assertThat(rule().deadlineAt()).isEqualTo(at.plusDays(9));assertThat(rule().warned()).isFalse();
        assertThat(jdbc.queryForObject("SELECT approval_status FROM crm_approval WHERE id=?",String.class,a)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_todo WHERE biz_type='APPROVAL' AND biz_id=? AND todo_status='PENDING'",Integer.class,a)).isZero();
        tick(at.plusDays(8));warn();assertThat(call("POST","/recycle/customers/"+f.customer+"/appeals",f.sales,appealBody()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** 新有效跟进改变周期后旧申请取消，不能将延期附加到新周期。 */
    @Test void staleAppealDoesNotExtendNewFollowCycle() {
        warn();long a=appeal();jdbc.update("UPDATE crm_customer SET last_effective_follow_at=? WHERE id=?",at,f.customer);
        ok(call("POST","/recycle/appeals/"+a+"/review",f.admin,reviewBody(true)));
        assertThat(jdbc.queryForObject("SELECT approval_status FROM crm_approval WHERE id=?",String.class,a)).isEqualTo("CANCELLED");
        assertThat(customers.active(f.customer).getRecycleExtensionUntil()).isNull();
    }

    /** 自动扫描清理已失效延期，不需经理处理旧周期，也不留下悬挂待办。 */
    @Test void scanClosesStaleAppealWithoutManagerAction() {
        warn();long a=appeal();enable(true);
        jdbc.update("UPDATE crm_customer SET last_effective_follow_at=? WHERE id=?",at,f.customer);
        scan();scan();assertPrivate();
        assertThat(jdbc.queryForObject("SELECT approval_status FROM crm_approval WHERE id=?",String.class,a)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_todo WHERE biz_type='APPROVAL' AND biz_id=? AND (todo_status='PENDING' OR resolved_at IS NULL)",Integer.class,a)).isZero();
        assertThat(customers.active(f.customer).getRecycleExtensionUntil()).isNull();
        assertThat(rule().decision()).isEqualTo("WAITING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=? AND action_code='RECYCLE_APPEAL_REVIEWED'",Integer.class,f.customer)).isEqualTo(1);
    }

    /** 常开扫描自动预警，无需管理员逐客户发送；重复扫描不重复通知。 */
    @Test void automaticWarningNeedsNoManualAction() {
        enable(true);scan();scan();assertPrivate();
        assertThat(todoCount()).isEqualTo(2);
        assertThat(rule().deadlineAt()).isEqualTo(at.plusHours(24));
        tick(at.plusHours(24));scan();assertThat(customers.active(f.customer).getSeaType()).isEqualTo("PUBLIC");
    }

    /** 常规旧客户补发预警后至少留三天，不因原期限已过而立即回收。 */
    @Test void lateFollowWarningLeavesThreeDays() {
        jdbc.update("UPDATE crm_customer SET last_effective_follow_at=?,ownership_started_at=? WHERE id=?",at.minusDays(50),at.minusDays(60),f.customer);
        warn();assertThat(rule().deadlineAt()).isEqualTo(at.plusDays(3));enable(true);scan();assertPrivate();
        tick(at.plusDays(3).minusNanos(1_000_000));scan();assertPrivate();tick(at.plusDays(3));scan();assertThat(customers.active(f.customer).getSeaType()).isEqualTo("PUBLIC");
    }

    /** 实际有效跟进接口解除旧预警待办，新的待核验记录保护客户。 */
    @Test void actualFollowResolvesWarningAndRefreshesClock() {
        warn();jdbc.update("INSERT INTO crm_contact(customer_id,contact_name,is_primary) VALUES(?,'隔离联系人',1)",f.customer);
        long contact=jdbc.queryForObject("SELECT id FROM crm_contact WHERE customer_id=?",Long.class,f.customer);
        var body=new HashMap<String,Object>();body.put("customerVersion",version());body.put("contactId",contact);body.put("followMethod","PHONE");body.put("content","已沟通实施时间与采购需求");body.put("resultCode","NEED_CONFIRMED");body.put("substantiveResult","明确了采购预算和审批人");body.put("nextAction","提交方案");body.put("nextActionAt",at.plusDays(1));body.put("followedAt",at);
        ok(call("POST","/customers/"+f.customer+"/follow-records",f.sales,body));assertThat(todoPending()).isZero();
        assertThat(rule().reason()).isEqualTo("有效跟进待核验");assertThat(rule().deadlineAt()).isEqualTo(at.plusDays(30));
        jdbc.update("DELETE FROM crm_follow_record WHERE customer_id=?",f.customer);jdbc.update("DELETE FROM crm_contact WHERE customer_id=?",f.customer);
    }

    /** 经理审批通过后不能以旧版本重复办理。 */
    @Test void duplicateReviewRejectedAndQuarterChangesAtNewYear() {
        warn();long a=appeal();ok(call("POST","/recycle/appeals/"+a+"/review",f.manager,reviewBody(false)));
        assertThat(call("POST","/recycle/appeals/"+a+"/review",f.admin,reviewBody(true)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call("POST","/recycle/customers/"+f.customer+"/appeals",f.sales,appealBody()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        tick(LocalDateTime.of(2027,1,1,0,0));appeal();
    }

    /** 待核验有效跟进阻止回收；打回后按最新事实重新判断。 */
    @Test void pendingFollowProtectsAndRejectedDoesNot() {
        warn();tick(at.plusDays(4));enable(true);
        jdbc.update("INSERT INTO crm_follow_record(customer_id,follow_user_id,record_type,follow_method,content,is_effective,review_status,followed_at,created_at) VALUES(?,?,'FOLLOW','PHONE','test',1,'PENDING',?,?)",f.customer,f.sales,at,at);
        scan();assertPrivate();jdbc.update("UPDATE crm_follow_record SET is_effective=0,review_status='REJECTED' WHERE customer_id=?",f.customer);scan();assertThat(customers.active(f.customer).getSeaType()).isEqualTo("PUBLIC");
    }

    /** 回收审计失败时客户归属和持有量均回滚，失败项可由统一任务入口补跑。 */
    @Test void recycleAuditRollbackAndRetry() {
        warn();tick(at.plusDays(4));enable(true);
        doThrow(new DataIntegrityViolationException("isolated recycle audit failure")).when(customers).ownershipAudit(eq(f.customer),eq("CUSTOMER_RECYCLED"),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
        var run=scan();assertThat(run.path("status").asText()).isEqualTo("FAILED");assertPrivate();
        reset(customers);ok(call("POST","/tasks/jobs/"+run.path("id").asLong()+"/retry",f.admin,null));assertThat(customers.active(f.customer).getSeaType()).isEqualTo("PUBLIC");
    }

    /** 并发扫描不能重复回收或重复减持有量。 */
    @Test void concurrentScansRecycleOnce() throws Exception {
        warn();tick(at.plusDays(4));enable(true);var pool=Executors.newFixedThreadPool(2);
        try {var a=pool.submit(this::scan);var b=pool.submit(this::scan);a.get(30,TimeUnit.SECONDS);b.get(30,TimeUnit.SECONDS);}
        finally {pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=? AND action_code='CUSTOMER_RECYCLED'",Integer.class,f.customer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT current_hold_count FROM sys_user WHERE id=?",Integer.class,f.sales)).isZero();
    }

    /** 规则查询后有人领取其他客户，回收仍按当前读重算持有量，不用旧事务快照。 */
    @Test void concurrentClaimDoesNotCorruptHoldCount() throws Exception {
        jdbc.update("INSERT INTO crm_customer(customer_name,normalized_name,sea_type,customer_status) VALUES(?,?,'PUBLIC','NEW')",f.prefix+"extra",f.prefix+"extra");
        long extra=jdbc.queryForObject("SELECT id FROM crm_customer WHERE customer_name=?",Long.class,f.prefix+"extra");extraCustomers.add(extra);
        warn();tick(at.plusDays(4));enable(true);
        var reached=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(inv->{reached.countDown();if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("test timed out");return 0L;}).when(mapper).openOpportunities(f.customer);
        var pool=Executors.newFixedThreadPool(1);
        try {
            var running=pool.submit(this::scan);assertThat(reached.await(15,TimeUnit.SECONDS)).isTrue();
            ok(call("POST","/customers/"+extra+"/claim?version=0",f.sales,null));release.countDown();running.get(30,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT current_hold_count FROM sys_user WHERE id=?",Integer.class,f.sales)).isEqualTo(1);
            assertThat(customers.active(f.customer).getSeaType()).isEqualTo("PUBLIC");
        } finally {release.countDown();pool.shutdownNow();}
    }

    /** 管理功能仅管理员，私海详情不能被同团队其他专员直接获取。 */
    @Test void permissionsAndConfigurationVersion() {
        for(Long user:List.of(f.sales,f.manager)) for(String suffix:List.of("/preview","/setting")) assertThat(call("GET","/recycle"+suffix,user,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call("GET","/recycle/customers/"+f.customer,f.peer,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var s=ok(call("GET","/recycle/setting",f.admin,null));enable(true);
        assertThat(call("PUT","/recycle/setting",f.admin,Map.of("enabled",false,"version",s.path("version").asLong())).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** 设置可控时钟，不修改系统时间。 */
    void tick(LocalDateTime value) {doReturn(value.atZone(ZoneId.of("Asia/Shanghai")).toInstant()).when(clock).instant();}
    /** 复用当前实际规则。 */
    com.bishe.crm.module.customer.vo.RecycleViews.Rule rule() {return recycle.rule(customers.active(f.customer),LocalDateTime.now(clock));}
    /** 获取客户最新版本。 */
    long version() {return customers.active(f.customer).getVersion();}
    /** 通过真实JWT发出预警。 */
    void warn() {ok(call("POST","/recycle/customers/"+f.customer+"/warning",f.admin,Map.of("version",version())));}
    /** 通过真实配置接口变更开关并清理时恢复。 */
    void enable(boolean value) {var s=ok(call("GET","/recycle/setting",f.admin,null));ok(call("PUT","/recycle/setting",f.admin,Map.of("enabled",value,"version",s.path("version").asLong())));}
    /** 扫描仅包含本测试候选。 */
    JsonNode scan() {return ok(call("POST","/recycle/scan",f.admin,null));}
    /** 当前客户预警数量。 */
    int todoCount() {return jdbc.queryForObject("SELECT COUNT(*) FROM crm_todo WHERE biz_type='CUSTOMER' AND biz_id=?",Integer.class,f.customer);}
    /** 未解除的客户预警数量。 */
    int todoPending() {return jdbc.queryForObject("SELECT COUNT(*) FROM crm_todo WHERE biz_type='CUSTOMER' AND biz_id=? AND todo_status='PENDING'",Integer.class,f.customer);}
    /** 延期申请使用实际客户版本。 */
    Map<String,Object> appealBody() {return Map.of("version",version(),"reason","客户出差待洽谈","nextPlan","下周再次沟通具体方案");}
    /** 提交并取得审批主键。 */
    long appeal() {return ok(call("POST","/recycle/customers/"+f.customer+"/appeals",f.sales,appealBody())).asLong();}
    /** 首次审批版本为0。 */
    Map<String,Object> reviewBody(boolean pass) {return Map.of("version",0,"pass",pass,"opinion","已核对延期计划");}
    /** 断言原归属未被修改。 */
    void assertPrivate() {assertThat(customers.active(f.customer).getOwnerId()).isEqualTo(f.sales);assertThat(customers.active(f.customer).getSeaType()).isEqualTo("PRIVATE");}
    /** 请求经过真实认证与后端权限。 */
    ResponseEntity<JsonNode> call(String method,String path,Long actor,Object body) {var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);h.setBearerAuth(tokens.issue(AuthPrincipal.from(users.findWithTeamById(actor))).value());return http.exchange("/api"+path,HttpMethod.valueOf(method),new HttpEntity<>(body,h),JsonNode.class);}
    /** 核对HTTP及业务响应。 */
    JsonNode ok(ResponseEntity<JsonNode> r) {assertThat(r.getStatusCode()).withFailMessage("%s",r.getBody()).isEqualTo(HttpStatus.OK);assertThat(r.getBody().path("code").asInt()).isZero();return r.getBody().path("data");}
}
