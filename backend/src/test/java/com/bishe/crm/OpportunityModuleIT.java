/* 文件作用：验证商机阶段、协作、活跃扫描和权限边界。 */
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

/** 验证商机阶段、协作、活跃扫描和权限边界。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class OpportunityModuleIT {

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtTokenService tokens;

    @Autowired
    SystemUserMapper users;

    @Autowired
    PasswordEncoder passwords;

    @Autowired
    OpportunityActivityService activity;

    @MockitoSpyBean
    OpportunityMapper opportunities;

    @MockitoSpyBean
    Clock businessClock;

    String prefix;
    Long east, south, admin, manager, sales, peer, third, fourth, foreign, customer;
    List<Long> actors = new ArrayList<>(),
        customers = new ArrayList<>();
    LocalDateTime now = LocalDateTime.of(2026, 10, 21, 12, 0);

    /** 准备隔离的测试账号、团队和业务数据。 */
    @BeforeEach
    void setup() {
        prefix = "op-" + UUID.randomUUID().toString().substring(0, 8);
        doReturn(now.atZone(ZoneId.of("Asia/Shanghai")).toInstant()).when(businessClock).instant();
        east = team("east");
        south = team("south");
        admin = user("admin", "ADMIN", null);
        manager = user("manager", "MANAGER", east);
        sales = user("sales", "SPECIALIST", east);
        peer = user("peer", "SPECIALIST", east);
        third = user("third", "SPECIALIST", east);
        fourth = user("fourth", "SPECIALIST", east);
        foreign = user("foreign", "SPECIALIST", south);
        customer = customer(sales, east, "FOLLOWING", "PRIVATE");
    }

    /** 清理本测试创建的隔离数据。 */
    @AfterEach
    void cleanup() {
        reset(opportunities, businessClock);
        for (Long c : customers) {
            for (Long o : jdbc.queryForList(
                "SELECT id FROM crm_opportunity WHERE customer_id=?",
                Long.class,
                c
            )) {
                jdbc.update("DELETE FROM crm_todo WHERE biz_type='OPPORTUNITY' AND biz_id=?", o);
                jdbc.update(
                    "DELETE FROM crm_operation_log WHERE biz_type='OPPORTUNITY' AND biz_id=?",
                    o
                );
                jdbc.update("DELETE FROM crm_follow_record WHERE opportunity_id=?", o);
                jdbc.update("DELETE FROM crm_contract WHERE opportunity_id=?", o);
                jdbc.update("DELETE FROM crm_opportunity_collaborator WHERE opportunity_id=?", o);
                jdbc.update("DELETE FROM crm_opportunity_stage_log WHERE opportunity_id=?", o);
                jdbc.update("DELETE FROM crm_opportunity WHERE id=?", o);
            }
            jdbc.update("DELETE FROM crm_follow_record WHERE customer_id=?", c);
            jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=?", c);
            jdbc.update("DELETE FROM crm_contact WHERE customer_id=?", c);
            jdbc.update("DELETE FROM crm_customer WHERE id=?", c);
        }
        for (Long a : actors) {
            jdbc.update("DELETE FROM crm_job_log WHERE triggered_by=?", a);
            jdbc.update("DELETE FROM crm_operation_log WHERE operator_id=?", a);
            jdbc.update("DELETE FROM sys_user WHERE id=?", a);
        }
        jdbc.update("DELETE FROM sys_team WHERE id IN (?,?)", east, south);
    }

    /** 验证创建商机归属一致、私海范围和必填字段。 */
    @Test
    void creationInheritsCustomerOwnerAndEnforcesPrivateScopeAndRequiredFields() {
        long id = create(admin, customer, "第一条", 100);
        JsonNode d = detail(sales, id);
        assertThat(d.path("ownerId").asLong()).isEqualTo(sales);
        assertThat(d.path("stageCode").asText()).isEqualTo("NEW");
        assertThat(d.path("graceDays").asInt()).isEqualTo(10);
        assertThat(
            jdbc.queryForObject(
                "SELECT customer_status FROM crm_customer WHERE id=?",
                String.class,
                customer
            )
        ).isEqualTo("PROTECTED");
        status(call("POST", "/api/opportunities", foreign, payload(customer, "越权", 1)), 403);
        long pub = customer(null, null, "NEW", "PUBLIC");
        status(call("POST", "/api/opportunities", admin, payload(pub, "公海", 1)), 403);
        long fresh = customer(sales, east, "NEW", "PRIVATE");
        status(call("POST", "/api/opportunities", admin, payload(fresh, "未首联", 1)), 400);
        var missing = payload(customer, "缺日期", 1);
        missing.remove("expectedCloseDate");
        status(call("POST", "/api/opportunities", admin, missing), 400);
        var negative = payload(customer, "负金额", 1);
        negative.put("expectedAmount", -1);
        status(call("POST", "/api/opportunities", admin, negative), 400);
    }

    /** 验证阶段相邻、业务证据、原因要求及审计历史。 */
    @Test
    void transitionsRequireEvidenceAdjacentStepsAndReasonsAndKeepAudit() {
        long id = create(admin, customer, "阶段", 100);
        status(stage(sales, id, "FOLLOWING", "专员直接推进"), 403);
        status(stage(manager, id, "FOLLOWING", "经理直接推进"), 403);
        status(stage(admin, id, "QUOTED", "越级"), 400);
        status(stage(admin, id, "FOLLOWING", "缺有效跟进"), 400);
        status(
            call(
                "POST",
                "/api/opportunities/" + id + "/evidence",
                sales,
                Map.of(
                    "version",
                    version(id),
                    "kind",
                    "QUOTE",
                    "quoteAmount",
                    200,
                    "description",
                    "不能绕过有效跟进"
                )
            ),
            400
        );
        follow(sales, id, "NOT_CONNECTED");
        assertThat(detail(sales, id).path("stageCode").asText()).isEqualTo("NEW");
        follow(sales, id, "NEED_CONFIRMED");
        assertThat(detail(sales, id).path("stageCode").asText()).isEqualTo("FOLLOWING");
        status(stage(admin, id, "NEW", null), 400);
        status(stage(admin, id, "QUOTED", "无报价"), 400);
        evidence(sales, id, "QUOTE", 200, "发送正式报价方案");
        assertThat(detail(sales, id).path("stageCode").asText()).isEqualTo("FOLLOWING");
        follow(sales, id, "SOLUTION_DISCUSSED");
        assertThat(detail(sales, id).path("stageCode").asText()).isEqualTo("QUOTED");
        assertThat(detail(sales, id).path("graceDays").asInt()).isEqualTo(15);
        status(stage(admin, id, "DEAL", "无客户确认"), 400);
        evidence(sales, id, "CONFIRMATION", 0, "客户电话明确确认合作");
        assertThat(detail(sales, id).path("stageCode").asText()).isEqualTo("QUOTED");
        follow(sales, id, "NEXT_STEP_AGREED");
        assertThat(detail(sales, id).path("stageCode").asText()).isEqualTo("DEAL");
        status(stage(admin, id, "WON", "无签署资料"), 400);
        assertThat(
            detail(sales, id).path("stageHistory").get(0).path("operatorName").asText()
        ).isEqualTo("系统自动");
        assertThat(
            detail(sales, id).path("stageHistory").get(0).path("operatorId").isNull()
        ).isTrue();
        ok(stage(admin, id, "QUOTED", "重新核对实施范围"));
        assertThat(detail(sales, id).path("stageHistory").size()).isEqualTo(5);
        status(stage(admin, id, "DEAL", "不得复用报价阶段旧轮次跟进"), 400);
        follow(sales, id, "NEED_CONFIRMED");
        assertThat(detail(sales, id).path("stageCode").asText()).isEqualTo("DEAL");
        assertThat(detail(sales, id).path("operationHistory").size()).isGreaterThanOrEqualTo(7);
    }

    /** 验证终态不可重开且仅管理员可关闭。 */
    @Test
    void terminalStateCannotReopenAndClosingIsAdminOnly() {
        long id = create(admin, customer, "关闭", 100);
        status(stage(sales, id, "CLOSED", "长期无推进"), 403);
        status(stage(manager, id, "CLOSED", "经理不能关闭"), 403);
        status(stage(admin, id, "CLOSED", null), 400);
        ok(stage(admin, id, "CLOSED", "管理员确认终止"));
        status(stage(admin, id, "NEW", "恢复"), 400);
        status(
            call(
                "PUT",
                "/api/opportunities/" + id,
                sales,
                Map.of(
                    "version",
                    version(id),
                    "opportunityName",
                    "修改终态",
                    "expectedAmount",
                    9,
                    "expectedCloseDate",
                    "2026-12-01"
                )
            ),
            400
        );
        assertThat(
            jdbc.queryForObject(
                "SELECT close_reason FROM crm_opportunity WHERE id=?",
                String.class,
                id
            )
        ).isEqualTo("管理员确认终止");
        assertThat(detail(manager, id).path("activityStatus").asText()).isEqualTo("TERMINATED");
        assertThat(
            jdbc.queryForObject(
                "SELECT customer_status FROM crm_customer WHERE id=?",
                String.class,
                customer
            )
        ).isEqualTo("NEW");
    }

    /** 验证有效合同阻止商机回退和输单。 */
    @Test
    void contractBlocksRollbackAndLosing() {
        long id = create(admin, customer, "合同保护", 100);
        follow(sales, id, "NEED_CONFIRMED");
        jdbc.update(
            "INSERT INTO crm_contract(contract_no,customer_id,opportunity_id,owner_id,team_id,contract_name,contract_amount,contract_status,created_by) VALUES(?,?,?,?,?,? ,100,'SIGNED',?)",
            prefix,
            customer,
            id,
            sales,
            east,
            prefix,
            sales
        );
        status(stage(admin, id, "NEW", "有合同回退"), 400);
        status(stage(admin, id, "LOST", "已有合同"), 400);
    }

    /** 验证关闭当前商机不取消其他商机保护及最终客户状态。 */
    @Test
    void closingOneOpportunityDoesNotEraseOtherProtectionOrFinalCustomerStates() {
        long first = create(admin, customer, "保护一", 100),
            second = create(admin, customer, "保护二", 100);
        ok(stage(admin, first, "CLOSED", "关闭其中一条"));
        assertThat(
            jdbc.queryForObject(
                "SELECT customer_status FROM crm_customer WHERE id=?",
                String.class,
                customer
            )
        ).isEqualTo("PROTECTED");
        jdbc.update("UPDATE crm_customer SET customer_status='DEAL_PENDING' WHERE id=?", customer);
        ok(stage(admin, second, "CLOSED", "独立商机关闭不得覆盖合同保护状态"));
        assertThat(
            jdbc.queryForObject(
                "SELECT customer_status FROM crm_customer WHERE id=?",
                String.class,
                customer
            )
        ).isEqualTo("DEAL_PENDING");
        jdbc.update("UPDATE crm_customer SET customer_status='DEAL' WHERE id=?", customer);
        opportunities.refreshCustomerProtection(customer, now);
        assertThat(
            jdbc.queryForObject(
                "SELECT customer_status FROM crm_customer WHERE id=?",
                String.class,
                customer
            )
        ).isEqualTo("DEAL");
    }

    /** 验证阈值只影响当前商机且编辑金额不刷新进展。 */
    @Test
    void thresholdsOnlyAffectOneOpportunityAndEditingMoneyDoesNotRefreshProgress() {
        long first = create(admin, customer, "阈值1", 100),
            second = create(admin, customer, "阈值2", 200);
        var baseline = jdbc.queryForMap(
            "SELECT stage_changed_at,last_effective_progress_at FROM crm_opportunity WHERE id=?",
            first
        );
        status(
            call(
                "PATCH",
                "/api/opportunities/" + first + "/threshold",
                sales,
                Map.of("version", 0, "stagnationDays", 9, "graceDays", 3)
            ),
            403
        );
        ok(
            call(
                "PATCH",
                "/api/opportunities/" + first + "/threshold",
                manager,
                Map.of("version", 0, "stagnationDays", 9, "graceDays", 3)
            )
        );
        follow(sales, first, "NEED_CONFIRMED");
        assertThat(detail(sales, first).path("stagnationDays").asInt()).isEqualTo(9);
        assertThat(detail(sales, second).path("stagnationDays").asInt()).isEqualTo(20);
        ok(
            call(
                "PUT",
                "/api/opportunities/" + first,
                sales,
                Map.of(
                    "version",
                    version(first),
                    "opportunityName",
                    "更新金额",
                    "expectedAmount",
                    1234.56,
                    "expectedCloseDate",
                    "2026-12-12"
                )
            )
        );
        assertThat(
            jdbc.queryForMap(
                "SELECT stage_changed_at,last_effective_progress_at FROM crm_opportunity WHERE id=?",
                first
            )
        ).isEqualTo(baseline);
    }

    /** 验证协作人窄范围读取、写入限制和版本检查。 */
    @Test
    void collaborationHasNarrowReadsAndWriteRestrictionsAndVersionChecks() {
        long id = create(admin, customer, "协作", 100);
        addMember(sales, id, peer, 10);
        var d = detail(peer, id);
        assertThat(d.path("canEdit").asBoolean()).isFalse();
        assertThat(d.path("contacts").size()).isEqualTo(1);
        assertThat(d.path("operationHistory").size()).isZero();
        status(call("GET", "/api/customers/" + customer, peer, null), 404);
        status(stage(peer, id, "FOLLOWING", null), 403);
        status(call("GET", "/api/opportunities/" + id + "/members", peer, null), 403);
        long member = d.path("collaborators").get(0).path("id").asLong();
        status(
            call(
                "DELETE",
                "/api/opportunities/" + id + "/collaborators/" + member + "?version=" + version(id),
                peer,
                null
            ),
            403
        );
        status(
            call(
                "POST",
                "/api/opportunities/" + id + "/collaborators",
                sales,
                Map.of("version", 0, "userId", third, "splitRatio", 10)
            ),
            409
        );
        addMember(sales, id, third, 10);
        addMember(sales, id, fourth, 10);
        status(
            call(
                "POST",
                "/api/opportunities/" + id + "/collaborators",
                sales,
                Map.of("version", version(id), "userId", manager, "splitRatio", 1)
            ),
            400
        );
        ok(
            call(
                "DELETE",
                "/api/opportunities/" + id + "/collaborators/" + member + "?version=" + version(id),
                sales,
                null
            )
        );
        status(call("GET", "/api/opportunities/" + id, peer, null), 404);
    }

    /** 验证协作分成和团队边界不可绕过。 */
    @Test
    void collaborationRatioAndTeamBoundariesCannotBeBypassed() {
        long id = create(admin, customer, "比例", 100);
        addMember(sales, id, peer, 25);
        status(
            call(
                "POST",
                "/api/opportunities/" + id + "/collaborators",
                sales,
                Map.of("version", version(id), "userId", third, "splitRatio", 6)
            ),
            400
        );
        status(
            call(
                "POST",
                "/api/opportunities/" + id + "/collaborators",
                sales,
                Map.of("version", version(id), "userId", foreign, "splitRatio", 1)
            ),
            403
        );
        status(
            call(
                "POST",
                "/api/opportunities/" + id + "/collaborators",
                sales,
                Map.of("version", version(id), "userId", manager, "splitRatio", 1)
            ),
            403
        );
    }

    /** 验证跟进只刷新关联商机且打回后重算计时。 */
    @Test
    void linkedFollowOnlyRefreshesCurrentOpportunityAndReturnRecalculatesClock() {
        long id = create(admin, customer, "跟进1", 100),
            other = create(admin, customer, "跟进2", 200);
        addMember(sales, id, peer, 10);
        var baseline = jdbc.queryForObject(
            "SELECT last_effective_progress_at FROM crm_opportunity WHERE id=?",
            LocalDateTime.class,
            other
        );
        now = now.plusDays(1);
        doReturn(now.atZone(ZoneId.of("Asia/Shanghai")).toInstant()).when(businessClock).instant();
        jdbc.update("UPDATE crm_customer SET version=256 WHERE id=?", customer);
        var d = detail(peer, id);
        var body = new HashMap<String, Object>(
            Map.of(
                "customerVersion",
                d.path("customerVersion").asLong(),
                "contactId",
                d.path("contacts").get(0).path("id").asLong(),
                "followMethod",
                "PHONE",
                "content",
                "已核对客户项目范围和计划",
                "resultCode",
                "NEED_CONFIRMED",
                "substantiveResult",
                "客户预算和采购时间已确认",
                "nextAction",
                "发送方案",
                "nextActionAt",
                now.plusDays(1).toString(),
                "followedAt",
                now.minusMinutes(1).toString()
            )
        );
        long follow = ok(
            call("POST", "/api/opportunities/" + id + "/follow-records", peer, body)
        ).asLong();
        assertThat(detail(sales, id).path("stageCode").asText()).isEqualTo("NEW");
        assertThat(detail(sales, other).path("stageCode").asText()).isEqualTo("NEW");
        assertThat(
            jdbc.queryForObject(
                "SELECT last_effective_progress_at FROM crm_opportunity WHERE id=?",
                LocalDateTime.class,
                id
            )
        ).isEqualTo(now.minusMinutes(1));
        assertThat(
            jdbc.queryForObject(
                "SELECT last_effective_progress_at FROM crm_opportunity WHERE id=?",
                LocalDateTime.class,
                other
            )
        ).isEqualTo(baseline);
        long cv = jdbc.queryForObject(
            "SELECT version FROM crm_customer WHERE id=?",
            Long.class,
            customer
        );
        ok(
            call(
                "PATCH",
                "/api/customers/" + customer + "/follow-records/" + follow + "/review",
                manager,
                Map.of("version", cv, "reviewStatus", "RETURNED", "reviewReason", "无实际沟通依据")
            )
        );
        assertThat(
            jdbc.queryForObject(
                "SELECT last_effective_progress_at FROM crm_opportunity WHERE id=?",
                LocalDateTime.class,
                id
            )
        ).isEqualTo(baseline);
        assertThat(
            detail(peer, id).path("followRecords").get(0).path("effective").asBoolean()
        ).isFalse();
        assertThat(
            detail(sales, id)
                .path("stageProgress")
                .get(1)
                .path("requirements")
                .get(0)
                .path("met")
                .asBoolean()
        ).isFalse();
        status(stage(admin, id, "QUOTED", "跟进已打回"), 400);
        now = baseline.plusDays(30);
        doReturn(now.atZone(ZoneId.of("Asia/Shanghai")).toInstant()).when(businessClock).instant();
        doReturn(List.of(id)).when(opportunities).activityCandidates();
        activity.scanNow(admin);
        // 跟进未核验，阶段仍为 NEW，按线索 20 天 + 10 天宽限判定。
        assertThat(detail(sales, id).path("activityStatus").asText()).isEqualTo("CLOSE_PENDING");
    }

    /** 验证漏斗数量、金额及角色筛选准确。 */
    @Test
    void funnelCountsAmountsAndRoleFiltersAreExact() {
        long id = create(admin, customer, "漏斗1", 100);
        create(admin, customer, "漏斗2", 200);
        follow(sales, id, "NEED_CONFIRMED");
        long other = customer(foreign, south, "FOLLOWING", "PRIVATE");
        create(admin, other, "其他团队", 900);
        JsonNode rows = ok(
            call("GET", "/api/opportunities/funnel?ownerId=" + sales, manager, null)
        );
        assertThat(rows.size()).isEqualTo(2);
        assertThat(rows.get(0).path("expectedAmount").decimalValue()).isEqualByComparingTo(
            "200.00"
        );
        assertThat(
            ok(call("GET", "/api/opportunities/funnel?ownerId=" + foreign, manager, null)).size()
        ).isZero();
        assertThat(
            ok(call("GET", "/api/opportunities/funnel?ownerId=" + sales, peer, null)).size()
        ).isZero();
        status(
            call("GET", "/api/opportunities/funnel?from=2026-12-01&to=2026-10-01", sales, null),
            400
        );
        assertThat(
            ok(call("GET", "/api/opportunities/funnel?from=2026-12-01", sales, null)).size()
        ).isZero();
    }

    /** 验证并发阶段请求仅成功一次且审计失败回滚。 */
    @Test
    void concurrentStageRequestsOnlyCommitOnceAndAuditFailureRollsBack() throws Exception {
        long id = create(admin, customer, "并发", 100),
            fresh = create(admin, customer, "自动事务回滚", 100);
        String auth = token(admin);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> {
                start.await();
                return raw(
                    "PATCH",
                    "/api/opportunities/" + id + "/stage",
                    auth,
                    Map.of("version", 0, "targetStage", "LOST", "reason", "并发管理员操作")
                )
                    .getStatusCode()
                    .value();
            });
            var b = pool.submit(() -> {
                start.await();
                return raw(
                    "PATCH",
                    "/api/opportunities/" + id + "/stage",
                    auth,
                    Map.of("version", 0, "targetStage", "LOST", "reason", "并发管理员操作")
                )
                    .getStatusCode()
                    .value();
            });
            start.countDown();
            assertThat(
                List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))
            ).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        long before = version(fresh);
        long customerBefore = jdbc.queryForObject(
            "SELECT version FROM crm_customer WHERE id=?",
            Long.class,
            customer
        );
        doThrow(new DataIntegrityViolationException("Injected opportunity audit failure"))
            .when(opportunities)
            .audit(
                eq(fresh),
                eq("OPPORTUNITY_STAGE_CHANGED"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        long followId = ok(
            call(
                "POST",
                "/api/opportunities/" + fresh + "/follow-records",
                sales,
                followBody(sales, fresh, "NEED_CONFIRMED")
            )
        ).asLong();
        JsonNode pending = detail(manager, fresh);
        status(
            call(
                "PATCH",
                "/api/customers/" + customer + "/follow-records/" + followId + "/review",
                manager,
                Map.of(
                    "version",
                    pending.path("customerVersion").asLong(),
                    "reviewStatus",
                    "CONFIRMED",
                    "reviewReason",
                    "触发流转审计故障"
                )
            ),
            409
        );
        assertThat(detail(manager, fresh).path("stageCode").asText()).isEqualTo("NEW");
        assertThat(detail(manager, fresh).path("stageHistory").size()).isEqualTo(1);
        assertThat(
            detail(manager, fresh).path("followRecords").get(0).path("reviewStatus").asText()
        ).isEqualTo("PENDING");
        assertThat(version(fresh)).isEqualTo(before + 1);
        assertThat(
            jdbc.queryForObject("SELECT version FROM crm_customer WHERE id=?", Long.class, customer)
        ).isEqualTo(customerBefore + 1);
    }

    /** 验证扫描时间边界、升级去重及裁决可重复执行。 */
    @Test
    void scanBoundaryEscalationDedupAndDecisionAreRepeatable() {
        long id = create(admin, customer, "扫描", 100);
        doReturn(List.of(id)).when(opportunities).activityCandidates();
        jdbc.update(
            "UPDATE crm_opportunity SET stage_changed_at=?,last_effective_progress_at=? WHERE id=?",
            now.minusDays(20),
            now.minusDays(20),
            id
        );
        assertThat(activity.scanNow(admin).get("changed")).isEqualTo(1);
        assertThat(detail(manager, id).path("activityStatus").asText()).isEqualTo("STAGNANT");
        int count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_todo WHERE biz_type='OPPORTUNITY' AND biz_id=?",
            Integer.class,
            id
        );
        activity.scanNow(admin);
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_todo WHERE biz_type='OPPORTUNITY' AND biz_id=?",
                Integer.class,
                id
            )
        ).isEqualTo(count);
        jdbc.update(
            "UPDATE crm_opportunity SET stage_changed_at=?,last_effective_progress_at=? WHERE id=?",
            now.minusDays(30),
            now.minusDays(30),
            id
        );
        activity.scanNow(admin);
        assertThat(detail(manager, id).path("activityStatus").asText()).isEqualTo("CLOSE_PENDING");
        jdbc.update(
            "UPDATE crm_opportunity SET close_pending_at=? WHERE id=?",
            now.minusDays(3),
            id
        );
        activity.scanNow(admin);
        var recipients = jdbc.queryForList(
            "SELECT recipient_id FROM crm_todo WHERE biz_type='OPPORTUNITY' AND biz_id=? AND todo_type='OPP_ESCALATED'",
            Long.class,
            id
        );
        assertThat(recipients).contains(admin, manager).doesNotContain(sales, peer, foreign);
        assertThat(recipients).doesNotHaveDuplicates();
        assertThat(
            jdbc.queryForObject(
                "SELECT sea_type FROM crm_customer WHERE id=?",
                String.class,
                customer
            )
        ).isEqualTo("PRIVATE");
        status(
            call(
                "POST",
                "/api/opportunities/" + id + "/close-decision",
                manager,
                Map.of("version", version(id), "close", true, "reason", "不能借裁决关闭")
            ),
            403
        );
        ok(
            call(
                "POST",
                "/api/opportunities/" + id + "/close-decision",
                manager,
                Map.of("version", version(id), "close", false, "reason", "已核实仍有合作意向")
            )
        );
        assertThat(detail(manager, id).path("activityStatus").asText()).isEqualTo("ACTIVE");
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_todo WHERE biz_type='OPPORTUNITY' AND biz_id=? AND todo_status='PENDING'",
                Integer.class,
                id
            )
        ).isZero();
    }

    /** 验证单条扫描失败隔离并记录日志，事务不残留部分审计。 */
    @Test
    void scanFailureIsIsolatedAndLoggedWithoutPartialAudit() {
        long first = create(admin, customer, "失败", 100),
            second = create(admin, customer, "成功", 100);
        doReturn(List.of(first, second)).when(opportunities).activityCandidates();
        jdbc.update(
            "UPDATE crm_opportunity SET stage_changed_at=?,last_effective_progress_at=? WHERE id IN (?,?)",
            now.minusDays(31),
            now.minusDays(31),
            first,
            second
        );
        doThrow(new DataIntegrityViolationException("Injected scan audit failure"))
            .when(opportunities)
            .audit(
                eq(first),
                eq("OPPORTUNITY_ACTIVITY_CHANGED"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        var result = activity.scanNow(admin);
        assertThat(result.get("failedIds")).isEqualTo(List.of(first));
        assertThat(detail(manager, first).path("activityStatus").asText()).isEqualTo("ACTIVE");
        assertThat(detail(manager, second).path("activityStatus").asText()).isEqualTo(
            "CLOSE_PENDING"
        );
        assertThat(
            jdbc.queryForObject(
                "SELECT run_status FROM crm_job_log WHERE triggered_by=? ORDER BY id DESC LIMIT 1",
                String.class,
                admin
            )
        ).isEqualTo("PARTIAL_FAILURE");
    }

    /** 验证三角色可见当前阶段及已完成、缺失门槛且不越权。 */
    @Test
    void allRolesSeeCurrentStageAndCompletedAndMissingRequirementsWithoutBroaderAccess() {
        long id = create(admin, customer, "门槛提示", 100);
        for (Long actor : List.of(sales, manager, admin)) {
            JsonNode d = detail(actor, id);
            assertThat(d.path("stageProgress").size()).isEqualTo(7);
            assertThat(d.path("stageProgress").get(0).path("current").asBoolean()).isTrue();
            assertThat(
                d.path("stageProgress").get(0).path("requirements").get(0).path("met").asBoolean()
            ).isFalse();
            assertThat(d.path("canChangeStage").asBoolean()).isEqualTo(actor.equals(admin));
        }
        status(call("GET", "/api/opportunities/" + id, foreign, null), 404);
        follow(sales, id, "NEED_CONFIRMED");
        for (Long actor : List.of(sales, manager, admin)) {
            JsonNode progress = detail(actor, id).path("stageProgress").get(1);
            assertThat(progress.path("current").asBoolean()).isTrue();
            assertThat(progress.path("requirements").get(0).path("met").asBoolean()).isFalse();
            assertThat(progress.path("requirements").get(1).path("met").asBoolean()).isFalse();
        }
        follow(sales, id, "SOLUTION_DISCUSSED");
        evidence(sales, id, "QUOTE", 100, "门槛报价");
        assertThat(
            detail(manager, id)
                .path("stageProgress")
                .get(2)
                .path("requirements")
                .get(1)
                .path("met")
                .asBoolean()
        ).isTrue();
        assertThat(
            detail(manager, id)
                .path("stageProgress")
                .get(2)
                .path("requirements")
                .get(2)
                .path("met")
                .asBoolean()
        ).isFalse();
    }

    /** 验证仅管理员新建并保持启用经理或专员归属一致。 */
    @Test
    void onlyAdminCreatesWithConsistentActiveManagerOrSpecialistOwnership() {
        var body = payload(customer, "权限分配", 100);
        body.put("ownerId", sales);
        for (Long actor : List.of(sales, manager, peer, foreign))
            status(call("POST", "/api/opportunities", actor, body), 403);
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_opportunity WHERE customer_id=?",
                Long.class,
                customer
            )
        ).isZero();
        assertThat(
            jdbc.queryForObject(
                "SELECT customer_status FROM crm_customer WHERE id=?",
                String.class,
                customer
            )
        ).isEqualTo("FOLLOWING");
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_operation_log WHERE operator_id IN (?,?,?,?,?)",
                Long.class,
                admin,
                sales,
                manager,
                peer,
                foreign
            )
        ).isZero();
        long id = ok(call("POST", "/api/opportunities", admin, body)).asLong();
        assertThat(detail(sales, id).path("ownerId").asLong()).isEqualTo(sales);
        long owned = customer(manager, east, "FOLLOWING", "PRIVATE");
        var managerBody = payload(owned, "经理自有", 200);
        managerBody.put("ownerId", manager);
        long managed = ok(call("POST", "/api/opportunities", admin, managerBody)).asLong();
        assertThat(detail(manager, managed).path("ownerId").asLong()).isEqualTo(manager);
        assertThat(detail(admin, managed).path("teamId").asLong()).isEqualTo(east);
        status(call("GET", "/api/opportunities/" + managed, sales, null), 404);
        managerBody.put("ownerId", sales);
        status(call("POST", "/api/opportunities", admin, managerBody), 409);
        assertThat(
            jdbc.queryForObject("SELECT owner_id FROM crm_customer WHERE id=?", Long.class, owned)
        ).isEqualTo(manager);
        jdbc.update("UPDATE sys_user SET account_status='DISABLED' WHERE id=?", manager);
        managerBody.put("ownerId", manager);
        status(call("POST", "/api/opportunities", admin, managerBody), 400);
        long mismatched = customer(peer, south, "FOLLOWING", "PRIVATE");
        status(
            call("POST", "/api/opportunities", admin, payload(mismatched, "团队不一致", 1)),
            400
        );
        long invalid = customer(admin, null, "FOLLOWING", "PRIVATE");
        status(
            call("POST", "/api/opportunities", admin, payload(invalid, "管理员不能负责", 1)),
            400
        );
    }

    /** 验证商机及相关分页接口固定每页十条。 */
    @Test
    void opportunityAndOtherPagedEndpointsUseTenRows() {
        for (int i = 0; i < 12; i++) create(admin, customer, "分页" + i, 100);
        var first = ok(call("GET", "/api/opportunities?page=1", sales, null));
        var second = ok(call("GET", "/api/opportunities?page=2", sales, null));
        assertThat(first.path("total").asInt()).isEqualTo(12);
        assertThat(first.path("list").size()).isEqualTo(10);
        assertThat(second.path("list").size()).isEqualTo(2);
        Set<Long> ids = new HashSet<>();
        first.path("list").forEach(x -> ids.add(x.path("id").asLong()));
        second.path("list").forEach(x -> ids.add(x.path("id").asLong()));
        assertThat(ids).hasSize(12);
        for (String path : List.of(
            "/api/opportunities",
            "/api/users",
            "/api/contracts",
            "/api/contracts/registration-opportunities"
        )) {
            status(call("GET", path + "?size=20", admin, null), 400);
            status(call("GET", path + "?size=10", admin, null), 200);
        }
    }

    /** 构造商机有效跟进请求。 */
    Map<String, Object> followBody(Long actor, long id, String result) {
        JsonNode d = detail(actor, id);
        return Map.of(
            "customerVersion",
            d.path("customerVersion").asLong(),
            "contactId",
            d.path("contacts").get(0).path("id").asLong(),
            "followMethod",
            "PHONE",
            "content",
            "已沟通项目需求和具体计划",
            "resultCode",
            result,
            "substantiveResult",
            "客户需求和采购时间已确认",
            "nextAction",
            "发送项目方案",
            "nextActionAt",
            now.plusDays(1).toString(),
            "followedAt",
            now.toString()
        );
    }

    /** 验证待审不推进、确认只推进一阶段及客户详情商机的权限范围。 */
    @Test
    void pendingFollowRequiresReviewAndCustomerDetailListsAuthorizedOpportunities() {
        long first = create(admin, customer, "客户商机一", 100);
        long second = create(admin, customer, "客户商机二", 200);
        long followId = ok(
            call(
                "POST",
                "/api/opportunities/" + first + "/follow-records",
                sales,
                followBody(sales, first, "NEED_CONFIRMED")
            )
        ).asLong();
        assertThat(detail(sales, first).path("stageCode").asText()).isEqualTo("NEW");
        JsonNode pending = detail(manager, first);
        status(
            call(
                "PATCH",
                "/api/customers/" + customer + "/follow-records/" + followId + "/review",
                sales,
                Map.of(
                    "version",
                    pending.path("customerVersion").asLong(),
                    "reviewStatus",
                    "CONFIRMED"
                )
            ),
            403
        );
        confirmFollow(first, followId);
        assertThat(detail(sales, first).path("stageCode").asText()).isEqualTo("FOLLOWING");
        assertThat(detail(sales, second).path("stageCode").asText()).isEqualTo("NEW");
        evidence(sales, first, "QUOTE", 200, "报价先保存等待本阶段跟进核验");
        assertThat(detail(sales, first).path("stageCode").asText()).isEqualTo("FOLLOWING");
        for (Long actor : List.of(sales, manager, admin)) {
            JsonNode customerDetail = ok(call("GET", "/api/customers/" + customer, actor, null));
            assertThat(customerDetail.path("opportunities").size()).isEqualTo(2);
            assertThat(
                customerDetail.path("opportunities").get(0).path("customerId").asLong()
            ).isEqualTo(customer);
        }
        status(call("GET", "/api/customers/" + customer, foreign, null), 404);
        assertThat(
            detail(manager, first).path("operationHistory").get(0).path("operatorName").asText()
        ).isNotBlank();
    }

    /** 管理角色提交立即核验且只推动本阶段；新阶段仍需新跟进。 */
    @Test
    void managementFollowIsConfirmedWithEvidenceAndAdvancesOnlyCurrentStage() {
        for (Long actor : List.of(manager, admin)) {
            long id = create(admin, customer, "管理角色自动核验" + actor, 100);
            long sibling = create(admin, customer, "不受影响商机" + actor, 100);
            long followId = ok(call("POST", "/api/opportunities/" + id + "/follow-records",
                actor, followBody(actor, id, "NEED_CONFIRMED"))).asLong();
            var record = jdbc.queryForMap("SELECT * FROM crm_follow_record WHERE id=?", followId);
            assertThat(record.get("review_status")).isEqualTo("CONFIRMED");
            assertThat(((Number) record.get("reviewer_id")).longValue()).isEqualTo(actor);
            assertThat(record.get("reviewed_at")).isNotNull();
            assertThat(record.get("review_reason").toString()).contains("自动核验");
            assertThat(record.get("opportunity_stage_code")).isEqualTo("NEW");
            assertThat(detail(actor, id).path("stageCode").asText()).isEqualTo("FOLLOWING");
            assertThat(detail(actor, sibling).path("stageCode").asText()).isEqualTo("NEW");
            evidence(sales, id, "QUOTE", 200, "等待当前阶段新跟进");
            assertThat(detail(actor, id).path("stageCode").asText()).isEqualTo("FOLLOWING");
            ok(call("POST", "/api/opportunities/" + id + "/follow-records",
                actor, followBody(actor, id, "SOLUTION_DISCUSSED")));
            assertThat(detail(actor, id).path("stageCode").asText()).isEqualTo("QUOTED");
        }
    }

    /** 管理角色无效结果不免检变有效；异团队仍不能提交。 */
    @Test
    void managementAutoReviewDoesNotPromoteUnansweredOrAllowForeignAccess() {
        long id = create(admin, customer, "无效跟进边界", 100);
        for (Long actor : List.of(manager, admin)) {
            long followId = ok(call("POST", "/api/opportunities/" + id + "/follow-records",
                actor, followBody(actor, id, "NOT_CONNECTED"))).asLong();
            assertThat(jdbc.queryForObject("SELECT review_status FROM crm_follow_record WHERE id=?",
                String.class, followId)).isEqualTo("NOT_REQUIRED");
            assertThat(jdbc.queryForObject("SELECT is_effective FROM crm_follow_record WHERE id=?",
                Boolean.class, followId)).isFalse();
            assertThat(detail(actor, id).path("stageCode").asText()).isEqualTo("NEW");
        }
        var denied = call("POST", "/api/opportunities/" + id + "/follow-records",
            foreign, followBody(sales, id, "NEED_CONFIRMED"));
        assertThat(denied.getStatusCode().value()).isIn(403, 404);
        long otherManager = user("other-manager", "MANAGER", south);
        var managerDenied = call("POST", "/api/opportunities/" + id + "/follow-records",
            otherManager, followBody(sales, id, "NEED_CONFIRMED"));
        assertThat(managerDenied.getStatusCode().value()).isIn(403, 404);
    }

    /** 自动核验与阶段推进失败必须回滚记录及客户时钟。 */
    @Test
    void autoReviewedFollowRollsBackWhenStageWriteFails() {
        long id = create(admin, customer, "自动核验回滚", 100);
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM crm_follow_record WHERE opportunity_id=?", Long.class, id);
        long version = detail(manager, id).path("customerVersion").asLong();
        doThrow(new DataIntegrityViolationException("isolated stage failure")).when(opportunities).insertStageLog(any());
        var response = call("POST", "/api/opportunities/" + id + "/follow-records", manager,
            followBody(manager, id, "NEED_CONFIRMED"));
        // 数据一致性失败按统一异常契约返回409；事务仍必须整笔回滚。
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_follow_record WHERE opportunity_id=?", Long.class, id)).isEqualTo(count);
        assertThat(detail(manager, id).path("stageCode").asText()).isEqualTo("NEW");
        assertThat(detail(manager, id).path("customerVersion").asLong()).isEqualTo(version);
    }

    /** 提交商机跟进并明确完成查验，仅用于需要已满足门槛的测试准备。 */
    void follow(Long actor, long id, String result) {
        long followId = ok(
            call(
                "POST",
                "/api/opportunities/" + id + "/follow-records",
                actor,
                followBody(actor, id, result)
            )
        ).asLong();
        if (!List.of("NO_RESPONSE", "NOT_CONNECTED").contains(result)) confirmFollow(id, followId);
    }

    /** 按当前客户版本由经理核验指定记录。 */
    void confirmFollow(long id, long followId) {
        JsonNode d = detail(manager, id);
        ok(
            call(
                "PATCH",
                "/api/customers/" +
                    d.path("customerId").asLong() +
                    "/follow-records/" +
                    followId +
                    "/review",
                manager,
                Map.of(
                    "version",
                    d.path("customerVersion").asLong(),
                    "reviewStatus",
                    "CONFIRMED",
                    "reviewReason",
                    "本阶段事实已核对"
                )
            )
        );
    }

    /** 创建隔离业务记录供后续场景使用。 */
    long create(Long actor, Long c, String name, int amount) {
        return ok(call("POST", "/api/opportunities", actor, payload(c, name, amount))).asLong();
    }

    /** 构造业务测试请求参数。 */
    Map<String, Object> payload(Long c, String name, int amount) {
        return new HashMap<>(
            Map.of(
                "customerId",
                c,
                "opportunityName",
                prefix + name,
                "expectedAmount",
                amount,
                "expectedCloseDate",
                "2026-12-01"
            )
        );
    }

    /** 读取最新记录版本供条件更新测试。 */
    long version(long id) {
        return jdbc.queryForObject(
            "SELECT version FROM crm_opportunity WHERE id=?",
            Long.class,
            id
        );
    }

    /** 查询详情并检查当前用户的数据权限。 */
    JsonNode detail(Long actor, long id) {
        return ok(call("GET", "/api/opportunities/" + id, actor, null));
    }

    /** 提交测试阶段修正请求。 */
    ResponseEntity<JsonNode> stage(Long actor, long id, String to, String reason) {
        var body = new HashMap<String, Object>(Map.of("version", version(id), "targetStage", to));
        if (reason != null) body.put("reason", reason);
        return call("PATCH", "/api/opportunities/" + id + "/stage", actor, body);
    }

    /** 提交测试报价或合作确认证据。 */
    void evidence(Long actor, long id, String kind, int amount, String text) {
        ok(
            call(
                "POST",
                "/api/opportunities/" + id + "/evidence",
                actor,
                Map.of(
                    "version",
                    version(id),
                    "kind",
                    kind,
                    "quoteAmount",
                    amount,
                    "description",
                    text
                )
            )
        );
    }

    /** 新增测试商机协作成员。 */
    void addMember(Long actor, long id, Long member, int ratio) {
        ok(
            call(
                "POST",
                "/api/opportunities/" + id + "/collaborators",
                actor,
                Map.of("version", version(id), "userId", member, "splitRatio", ratio)
            )
        );
    }

    /** 创建测试用团队。 */
    Long team(String suffix) {
        jdbc.update(
            "INSERT INTO sys_team(team_code,team_name) VALUES(?,?)",
            prefix + suffix,
            prefix + suffix
        );
        return jdbc.queryForObject(
            "SELECT id FROM sys_team WHERE team_code=?",
            Long.class,
            prefix + suffix
        );
    }

    /** 创建测试用账号。 */
    Long user(String suffix, String role, Long team) {
        jdbc.update(
            "INSERT INTO sys_user(username,password_hash,real_name,role_code,team_id,must_change_password) VALUES(?,?,?,?,?,0)",
            prefix + suffix,
            passwords.encode("OpportunityFixture123!"),
            suffix,
            role,
            team
        );
        Long id = jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE username=?",
            Long.class,
            prefix + suffix
        );
        actors.add(id);
        return id;
    }

    /** 创建测试用客户。 */
    Long customer(Long owner, Long team, String state, String sea) {
        String name = prefix + "-" + customers.size();
        jdbc.update(
            "INSERT INTO crm_customer(customer_name,normalized_name,owner_id,team_id,customer_status,sea_type,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?)",
            name,
            name,
            owner,
            team,
            state,
            sea,
            admin,
            admin
        );
        Long id = jdbc.queryForObject(
            "SELECT id FROM crm_customer WHERE customer_name=?",
            Long.class,
            name
        );
        customers.add(id);
        jdbc.update(
            "INSERT INTO crm_contact(customer_id,contact_name,phone,is_primary) VALUES(?,'临时联系人','13812345678',1)",
            id
        );
        return id;
    }

    /** 取得测试账号的有效登录令牌。 */
    String token(Long actor) {
        return tokens.issue(AuthPrincipal.from(users.findWithTeamById(actor))).value();
    }

    /** 发送带身份信息的测试 HTTP 请求。 */
    ResponseEntity<JsonNode> call(String method, String url, Long actor, Object body) {
        return raw(method, url, actor == null ? null : token(actor), body);
    }

    /** 发送请求并保留原始响应供断言。 */
    ResponseEntity<JsonNode> raw(String method, String url, String token, Object body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(
            url,
            HttpMethod.valueOf(method),
            new HttpEntity<>(body, headers),
            JsonNode.class
        );
    }

    /** 断言接口成功并读取返回数据。 */
    JsonNode ok(ResponseEntity<JsonNode> response) {
        status(response, 200);
        assertThat(response.getBody().path("code").asInt()).isZero();
        return response.getBody().path("data");
    }

    /** 返回或断言 HTTP 状态。 */
    void status(ResponseEntity<JsonNode> response, int expected) {
        assertThat(response.getStatusCode().value())
            .withFailMessage("%s", response.getBody())
            .isEqualTo(expected);
    }
}
