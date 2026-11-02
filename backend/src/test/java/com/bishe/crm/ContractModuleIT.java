/* 文件作用：验证合同登记、资料版本与签署进度的完整约束。 */
package com.bishe.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.auth.security.JwtTokenService;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/** 验证合同登记、资料版本与签署进度的完整约束。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class ContractModuleIT {

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

    String prefix;
    Long team;
    Long manager;
    Long sales;
    Long admin;
    Long customer;
    Long opportunity;
    Long contract;
    List<Long> extraUsers = new ArrayList<>();
    List<Long> extraContracts = new ArrayList<>();
    Long otherTeam;

    /** 准备隔离的测试账号、团队和业务数据。 */
    @BeforeEach
    void setup() {
        prefix = "contract-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO sys_team(team_code,team_name) VALUES(?,?)", prefix, prefix);
        team = jdbc.queryForObject("SELECT id FROM sys_team WHERE team_code=?", Long.class, prefix);
        manager = user("manager", "MANAGER", team);
        sales = user("sales", "SPECIALIST", team);
        admin = user("admin", "ADMIN", null);
        extraUsers.add(admin);
        jdbc.update(
            "INSERT INTO crm_customer(customer_name,normalized_name,owner_id,team_id,customer_status,sea_type,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?)",
            prefix,
            prefix,
            sales,
            team,
            "FOLLOWING",
            "PRIVATE",
            manager,
            manager
        );
        customer = jdbc.queryForObject(
            "SELECT id FROM crm_customer WHERE customer_name=?",
            Long.class,
            prefix
        );
        jdbc.update(
            "INSERT INTO crm_contact(customer_id,contact_name,phone,is_primary) VALUES(?,'合同联系人','13900000000',1)",
            customer
        );
        opportunity = createOpportunity();
        confirmedFollow(); // NEW -> FOLLOWING
        confirmedFollow(); // FOLLOWING 本轮
        evidence("QUOTE", "正式报价");
        confirmedFollow(); // QUOTED 本轮
        evidence("CONFIRMATION", "客户确认合作");
        assertThat(
            ok(call("GET", "/api/opportunities/" + opportunity, sales, null))
                .path("stageCode")
                .asText()
        ).isEqualTo("DEAL");
    }

    /** 清理本测试创建的隔离数据。 */
    @AfterEach
    void cleanup() {
        if (opportunity != null) jdbc.update(
            "DELETE FROM crm_opportunity_collaborator WHERE opportunity_id=?",
            opportunity
        );
        if (contract != null) {
            for (String path : jdbc.queryForList(
                "SELECT storage_path FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=?",
                String.class,
                contract
            )) {
                try {
                    Files.deleteIfExists(Path.of(path));
                } catch (Exception ignored) {}
            }
            jdbc.update(
                "DELETE FROM crm_operation_log WHERE biz_type='CONTRACT' AND biz_id=?",
                contract
            );
            jdbc.update("DELETE FROM crm_contract_signing_history WHERE contract_id=?", contract);
            jdbc.update(
                "DELETE FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=?",
                contract
            );
            jdbc.update("DELETE FROM crm_contract WHERE id=?", contract);
        }
        for (Long id : extraContracts) {
            jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='CONTRACT' AND biz_id=?", id);
            jdbc.update("DELETE FROM crm_contract_signing_history WHERE contract_id=?", id);
            jdbc.update("DELETE FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=?", id);
            jdbc.update("DELETE FROM crm_contract WHERE id=?", id);
        }
        if (opportunity != null) {
            List<Long> fixtureContracts = jdbc.queryForList(
                "SELECT id FROM crm_contract WHERE opportunity_id=?",
                Long.class,
                opportunity
            );
            for (Long id : fixtureContracts) {
                for (String path : jdbc.queryForList(
                    "SELECT storage_path FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=?",
                    String.class,
                    id
                )) {
                    try {
                        Files.deleteIfExists(Path.of(path));
                    } catch (Exception ignored) {}
                }
                jdbc.update(
                    "DELETE FROM crm_operation_log WHERE biz_type='CONTRACT' AND biz_id=?",
                    id
                );
                jdbc.update("DELETE FROM crm_contract_signing_history WHERE contract_id=?", id);
                jdbc.update(
                    "DELETE FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=?",
                    id
                );
                jdbc.update("DELETE FROM crm_contract WHERE id=?", id);
            }
        }
        if (customer != null) {
            jdbc.update(
                "DELETE l FROM crm_operation_log l JOIN crm_opportunity o ON l.biz_type='OPPORTUNITY' AND l.biz_id=o.id WHERE o.customer_id=?",
                customer
            );
            jdbc.update(
                "DELETE l FROM crm_opportunity_stage_log l JOIN crm_opportunity o ON o.id=l.opportunity_id WHERE o.customer_id=?",
                customer
            );
            jdbc.update("DELETE FROM crm_follow_record WHERE customer_id=?", customer);
            jdbc.update("DELETE FROM crm_opportunity WHERE customer_id=?", customer);
        }
        if (customer != null) {
            jdbc.update(
                "DELETE FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=?",
                customer
            );
            jdbc.update("DELETE FROM crm_follow_record WHERE customer_id=?", customer);
            jdbc.update("DELETE FROM crm_contact WHERE customer_id=?", customer);
            jdbc.update("DELETE FROM crm_customer WHERE id=?", customer);
        }
        if (sales != null) jdbc.update("DELETE FROM sys_user WHERE id=?", sales);
        if (manager != null) jdbc.update("DELETE FROM sys_user WHERE id=?", manager);
        for (Long id : extraUsers) jdbc.update("DELETE FROM sys_user WHERE id=?", id);
        if (team != null) jdbc.update("DELETE FROM sys_team WHERE id=?", team);
        if (otherTeam != null) jdbc.update("DELETE FROM sys_team WHERE id=?", otherTeam);
    }

    /** 验证五步真实流转、两次退回、新版本和成交阶段跟进门槛。 */
    @Test
    void fiveStepsRequireSpecificFilesReviewAndCurrentStageFollow() {
        createContract();
        assertStep("DRAFT_GENERATED");
        assertThat(upload("SIGNED_CONTRACT").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
            updateProgress(admin, "FILE_CONFIRMED", "不能人工跳步").getStatusCode()
        ).isEqualTo(HttpStatus.BAD_REQUEST);
        complete();
        ok(draftSubmit(sales));
        assertStep("DRAFT_REVIEW");
        assertThat(review(sales, true).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ok(review(manager, false));
        assertStep("DRAFT_GENERATED");
        assertThat(submit(sales).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        complete();
        ok(draftSubmit(sales));
        ok(review(admin, true));
        assertStep("SIGNED_UPLOAD");
        assertThat(submit(sales).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        complete();
        ok(upload("SIGNED_CONTRACT"));
        assertStep("SIGNED_UPLOAD"); // 上传本身不能代替负责人确定
        assertThat(submit(manager).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ok(submit(sales));
        assertStep("FILE_REVIEW");
        ok(review(manager, false));
        assertStep("SIGNED_UPLOAD");
        assertThat(submit(sales).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST); // 退回版本不可原样重提
        ok(upload("SIGNED_CONTRACT"));
        ok(submit(admin));
        ok(review(manager, true));
        assertStep("FILE_CONFIRMED");
        assertThat(cd().path("contractStatus").asText()).isEqualTo("SIGNED");
        assertThat(jdbc.queryForObject("SELECT performance_owner_id FROM crm_contract WHERE id=?",Long.class,contract)).isEqualTo(sales);
        assertThat(jdbc.queryForObject("SELECT performance_team_id FROM crm_contract WHERE id=?",Long.class,contract)).isEqualTo(team);
        assertThat(jdbc.queryForObject("SELECT performance_confirmed_at IS NOT NULL FROM crm_contract WHERE id=?",Boolean.class,contract)).isTrue();
        assertThat(jdbc.queryForObject("SELECT performance_legacy FROM crm_contract WHERE id=?",Integer.class,contract)).isZero();
        assertThat(
            ok(call("GET", "/api/opportunities/" + opportunity, sales, null))
                .path("stageCode")
                .asText()
        ).isEqualTo("DEAL");
        confirmedFollow();
        JsonNode won = ok(call("GET", "/api/opportunities/" + opportunity, sales, null));
        assertThat(won.path("stageCode").asText()).isEqualTo("WON");
        assertThat(cd().path("attachments").size()).isEqualTo(2);
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=? AND confirm_status='REJECTED'",
                Integer.class,
                contract
            )
        ).isEqualTo(1);
        assertThat(cd().path("operationHistory").get(0).path("operatorName").asText()).contains(
            prefix
        );
        assertThat(cd().path("signingHistory").get(0).path("changedAt").asText()).doesNotContain(
            "."
        );
        // 已确认结果可暂停/恢复，不能撤销已确认事实或新建第二份有效登记。
        ok(updateProgress(sales, "WITHDRAWN", "暂缓归档"));
        assertThat(cd().path("contractStatus").asText()).isEqualTo("SIGNED");
        ok(updateProgress(admin, "NORMAL", "核对原文件后恢复"));
        assertStep("FILE_CONFIRMED");
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=? AND confirm_status='PASSED'",
                Integer.class,
                contract
            )
        ).isEqualTo(1);
    }

    /** 在线草稿无需文件，可反复保存、撤回和重提；最新签署文件才被核验。 */
    @Test
    void onlineRegistrationSupportsEditWithdrawReturnAndLatestSignedVersion() {
        createContract();
        assertThat(draftSubmit(sales).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        complete();
        assertThat(cd().path("ourContactName").asText()).isEqualTo("我方经办人");
        assertThat(cd().path("counterpartyContactName").asText()).isEqualTo("客户联系人");
        assertThat(cd().path("paymentTerms").asText()).contains("分期");
        assertThat(cd().path("attachments").size()).isZero();
        long staleVersion = cd().path("version").asLong();
        complete();
        assertThat(
            call(
                "POST",
                "/api/contracts/" + contract + "/draft-submit",
                sales,
                Map.of("version", staleVersion)
            ).getStatusCode()
        ).isEqualTo(HttpStatus.CONFLICT);
        assertThat(draftSubmit(manager).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ok(draftSubmit(sales));
        assertStep("DRAFT_REVIEW");
        assertThat(cd().path("attachments").size()).isZero();
        assertThat(withdrawDraft(manager).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ok(withdrawDraft(sales));
        assertStep("DRAFT_GENERATED");
        assertThat(cd().path("canEdit").asBoolean()).isTrue();
        complete();
        ok(draftSubmit(admin));
        ok(review(manager, false));
        assertStep("DRAFT_GENERATED");
        complete();
        ok(draftSubmit(sales));
        ok(review(manager, true));
        assertStep("SIGNED_UPLOAD");
        assertThat(cd().path("registrationApprovedAt").isNull()).isFalse();
        // 补实际日期不撤销批准；修改关键登记信息则重新回到草稿核对。
        Map<String, Object> changed = registrationFields();
        changed.put("version", cd().path("version").asLong());
        changed.put("counterpartyContactPhone", "13800000009");
        ok(call("PUT", "/api/contracts/" + contract, sales, changed));
        assertStep("DRAFT_GENERATED");
        assertThat(cd().path("registrationApprovedAt").isNull()).isTrue();
        ok(draftSubmit(sales));
        ok(review(manager, true));
        ok(upload("SIGNED_CONTRACT"));
        long firstId = cd().path("attachments").get(0).path("id").asLong();
        ok(upload("SIGNED_CONTRACT"));
        long secondId = cd().path("attachments").get(0).path("id").asLong();
        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(cd().path("attachments").size()).isEqualTo(2);
        // 本地两个历史版本均可通过受保护接口下载；三角色沿用原数据范围。
        for (Long actor : List.of(sales, manager, admin)) {
            HttpHeaders downloadHeaders = new HttpHeaders();
            downloadHeaders.setBearerAuth(token(actor));
            for (long attachmentId : List.of(firstId, secondId)) {
                String path = "/api/contracts/" + contract + "/attachments/" + attachmentId + "/download";
                var downloaded = http.exchange(path, HttpMethod.GET,
                    new HttpEntity<>(downloadHeaders), byte[].class);
                assertThat(downloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(downloaded.getBody()).isEqualTo("%PDF-1.4 test fixture".getBytes());
                assertThat(downloaded.getHeaders().getCacheControl()).contains("no-store");
                assertThat(downloaded.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
            }
        }
        String downloadPath = "/api/contracts/" + contract + "/attachments/" + firstId + "/download";
        assertThat(http.getForEntity(downloadPath, byte[].class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Long unrelated = user("unrelated", "SPECIALIST", team);
        extraUsers.add(unrelated);
        assertThat(call("GET", downloadPath, unrelated, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ok(submit(sales));
        ok(review(manager, true));
        assertStep("FILE_CONFIRMED");
        assertThat(
            jdbc.queryForObject(
                "SELECT confirm_status FROM crm_attachment WHERE id=?",
                String.class,
                secondId
            )
        ).isEqualTo("PASSED");
        assertThat(
            jdbc.queryForObject(
                "SELECT confirm_status FROM crm_attachment WHERE id=?",
                String.class,
                firstId
            )
        ).isEqualTo("PENDING");
    }

    /** 非法字段给出明确提示且不写库，修正后可用同一版本继续保存。 */
    @Test
    void registrationValidationIdentifiesFieldsAndKeepsFailedSaveUnchanged() {
        createContract();
        JsonNode before = cd();
        Map<String, Object> values = registrationFields();
        values.put("version", before.path("version").asLong());
        values.put("ourCreditCode", "BAD-CODE");
        values.put("counterpartyContactPhone", "私人号码");
        ResponseEntity<JsonNode> rejected = call(
            "PUT",
            "/api/contracts/" + contract,
            sales,
            values
        );
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().path("code").asInt()).isEqualTo(40001);
        assertThat(
            rejected.getBody().path("data").path("fieldErrors").path("ourCreditCode").asText()
        ).contains("我方统一社会信用代码", "18位");
        assertThat(
            rejected
                .getBody()
                .path("data")
                .path("fieldErrors")
                .path("counterpartyContactPhone")
                .asText()
        ).contains("对方联系电话");
        assertThat(rejected.getBody().toString()).doesNotContain(
            "BAD-CODE",
            "私人号码",
            "rejectedValue"
        );
        assertThat(cd().path("version")).isEqualTo(before.path("version"));
        assertThat(cd().path("contractName")).isEqualTo(before.path("contractName"));
        assertThat(
            jdbc.queryForObject(
                "SELECT our_credit_code FROM crm_contract WHERE id=?",
                String.class,
                contract
            )
        ).isNull();

        values.put("ourCreditCode", "91310000ABCDEFGH12");
        values.put("counterpartyContactPhone", "+86 (021) 1234-5678");
        values.put("signedAt", "");
        values.put("performanceStartDate", "2026-10-31");
        values.put("expireDate", "2026-10-31");
        ok(call("PUT", "/api/contracts/" + contract, sales, values));
        assertThat(cd().path("signedAt").isNull()).isTrue();
        assertThat(cd().path("ourCreditCode").asText()).isEqualTo("91310000ABCDEFGH12");
        assertThat(
            jdbc.queryForObject(
                "SELECT counterparty_contact_phone FROM crm_contract WHERE id=?",
                String.class,
                contract
            )
        ).isEqualTo("+86 (021) 1234-5678");

        values.put("version", cd().path("version").asLong());
        values.put("description", "修正后再次保存的登记内容");
        ok(call("PUT", "/api/contracts/" + contract, sales, values));
        assertThat(cd().path("description").asText()).isEqualTo("修正后再次保存的登记内容");
        assertStep("DRAFT_GENERATED");
    }

    /** 提交当前在线登记表，不上传草稿附件。 */
    ResponseEntity<JsonNode> draftSubmit(Long actor) {
        return call(
            "POST",
            "/api/contracts/" + contract + "/draft-submit",
            actor,
            Map.of("version", cd().path("version").asLong())
        );
    }

    /** 负责人撤回待审登记供再次编辑。 */
    ResponseEntity<JsonNode> withdrawDraft(Long actor) {
        return call(
            "POST",
            "/api/contracts/" + contract + "/draft-withdraw",
            actor,
            Map.of("version", cd().path("version").asLong(), "reason", "补充联系信息")
        );
    }

    /** 验证暂停恢复权限、原节点保存、版本冲突和材料缺失的拒绝。 */
    @Test
    void pauseRestoreRechecksFilesAndScopeWithoutBypassingReview() throws Exception {
        createContract();
        Long peer = user("peer", "SPECIALIST", team);
        extraUsers.add(peer);
        assertThat(updateProgress(peer, "EXCEPTION", "越权").getStatusCode()).isEqualTo(
            HttpStatus.NOT_FOUND
        );
        complete();
        ok(draftSubmit(sales));
        long oldVersion = cd().path("version").asLong();
        ok(updateProgress(sales, "EXCEPTION", "资料待补正"));
        assertThat(cd().path("resumeSigningProgress").asText()).isEqualTo("DRAFT_REVIEW");
        assertThat(updateProgress(sales, "NORMAL", "专员不得自行恢复").getStatusCode()).isEqualTo(
            HttpStatus.FORBIDDEN
        );
        assertThat(review(manager, true).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
            call(
                "PATCH",
                "/api/contracts/" + contract + "/signing-progress",
                manager,
                Map.of("version", oldVersion, "targetProgress", "NORMAL", "reason", "旧版本")
            ).getStatusCode()
        ).isEqualTo(HttpStatus.CONFLICT);
        assertStep("EXCEPTION");
        ok(updateProgress(manager, "NORMAL", "恢复在线登记核对，无需草稿文件"));
        assertStep("DRAFT_REVIEW");
        ok(review(manager, true));
        assertStep("SIGNED_UPLOAD");
        ok(upload("SIGNED_CONTRACT"));
        ok(submit(sales));
        ok(updateProgress(sales, "EXCEPTION", "检查签署材料"));
        String path = jdbc.queryForObject(
            "SELECT storage_path FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=? ORDER BY id DESC LIMIT 1",
            String.class,
            contract
        );
        Path original = Path.of(path),
            temp = original.resolveSibling(original.getFileName() + ".testbak");
        Files.move(original, temp);
        try {
            assertThat(
                updateProgress(manager, "NORMAL", "文件缺失不得恢复待确认").getStatusCode()
            ).isEqualTo(HttpStatus.BAD_REQUEST);
        } finally {
            Files.move(temp, original);
        }
        ok(updateProgress(manager, "NORMAL", "原文件恢复"));
        assertStep("FILE_REVIEW");
        ok(updateProgress(manager, "WITHDRAWN", "暂停办理"));
        ok(updateProgress(admin, "NORMAL", "恢复原待核验步骤"));
        assertStep("FILE_REVIEW");
        assertThat(
            call(
                "POST",
                "/api/contracts/from-opportunity",
                sales,
                Map.of("opportunityId", opportunity)
            ).getStatusCode()
        ).isEqualTo(HttpStatus.CONFLICT);
    }

    /** 经理作为负责人时由管理员审核，不允许审核自己的合同。 */
    @Test
    void managerOwnerSubmitsButRequiresAnotherReviewer() {
        jdbc.update("UPDATE crm_opportunity SET owner_id=? WHERE id=?", manager, opportunity);
        createContract();
        complete();
        ok(draftSubmit(manager));
        assertThat(review(manager, true).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ok(review(admin, true));
        complete();
        ok(uploadAs(manager, "SIGNED_CONTRACT"));
        ok(submit(manager));
        ok(review(admin, true));
        assertStep("FILE_CONFIRMED");
    }

    /** 创建登记测试对象。 */
    void createContract() {
        contract = ok(
            call(
                "POST",
                "/api/contracts/from-opportunity",
                admin,
                Map.of("opportunityId", opportunity)
            )
        ).asLong();
    }

    /** 读取管理员详情。 */
    JsonNode cd() {
        return ok(call("GET", "/api/contracts/" + contract, admin, null));
    }

    /** 断言持久化步骤与接口一致。 */
    void assertStep(String step) {
        assertThat(cd().path("signingProgress").asText()).isEqualTo(step);
        assertThat(
            jdbc.queryForObject(
                "SELECT signing_progress FROM crm_contract WHERE id=?",
                String.class,
                contract
            )
        ).isEqualTo(step);
    }

    /** 提供完整的登记字段，日期与实际合同文件独立填写。 */
    Map<String, Object> registrationFields() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("contractName", prefix + "合同");
        values.put("businessType", "软件服务");
        values.put("ourPartyName", "我方主体");
        values.put("counterpartyName", "客户主体");
        values.put("ourContactName", "我方经办人");
        values.put("ourContactPhone", "13900000001");
        values.put("counterpartyContactName", "客户联系人");
        values.put("counterpartyContactPhone", "13800000001");
        values.put("contractAmount", 1200.50);
        values.put("signedAt", "2026-10-20");
        values.put("description", "软件服务范围及交付内容");
        values.put("paymentTerms", "按约定分期支付");
        values.put("deliveryTerms", "分阶段交付并验收");
        return values;
    }

    /** 通过真实编辑接口保存登记信息，可重复修改。 */
    void complete() {
        Map<String, Object> values = registrationFields();
        values.put("version", cd().path("version").asLong());
        ok(call("PUT", "/api/contracts/" + contract, admin, values));
    }

    /** 由默认负责人上传文件。 */
    ResponseEntity<JsonNode> upload(String type) {
        return uploadAs(sales, type);
    }

    /** 上传真实隔离测试文件并走 HTTP multipart。 */
    ResponseEntity<JsonNode> uploadAs(Long actor, String type) {
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("materialType", type);
        parts.part(
            "file",
            new ByteArrayResource("%PDF-1.4 test fixture".getBytes()) {
                @Override
                public String getFilename() {
                    return "test-contract.pdf";
                }
            },
            MediaType.APPLICATION_PDF
        );
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token(actor));
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.exchange(
            "/api/contracts/" + contract + "/attachments",
            HttpMethod.POST,
            new HttpEntity<>(parts.build(), h),
            JsonNode.class
        );
    }

    /** 提交实际签署文件。 */
    ResponseEntity<JsonNode> submit(Long actor) {
        return call(
            "POST",
            "/api/contracts/" + contract + "/submit",
            actor,
            Map.of("version", cd().path("version").asLong())
        );
    }

    /** 审核当前材料。 */
    ResponseEntity<JsonNode> review(Long actor, boolean pass) {
        return call(
            "POST",
            "/api/contracts/" + contract + "/review",
            actor,
            Map.of(
                "version",
                cd().path("version").asLong(),
                "pass",
                pass,
                "opinion",
                pass ? "已查验资料" : "补充清晰签署页"
            )
        );
    }

    /** 当前阶段新增跟进，通过真实查验接口完成推进门槛。 */
    void confirmedFollow() {
        JsonNode d = ok(call("GET", "/api/opportunities/" + opportunity, sales, null));
        java.time.LocalDateTime now = java.time.LocalDateTime.now(
            java.time.ZoneId.of("Asia/Shanghai")
        );
        Long id = ok(
            call(
                "POST",
                "/api/opportunities/" + opportunity + "/follow-records",
                sales,
                Map.of(
                    "customerVersion",
                    d.path("customerVersion").asLong(),
                    "contactId",
                    d.path("contacts").get(0).path("id").asLong(),
                    "followMethod",
                    "PHONE",
                    "content",
                    "已沟通本阶段需求及后续安排",
                    "resultCode",
                    "NEED_CONFIRMED",
                    "substantiveResult",
                    "本阶段采购安排已明确",
                    "nextAction",
                    "发送方案",
                    "nextActionAt",
                    now.plusDays(1).toString(),
                    "followedAt",
                    now.minusSeconds(1).toString()
                )
            )
        ).asLong();
        long cv = jdbc.queryForObject(
            "SELECT version FROM crm_customer WHERE id=?",
            Long.class,
            customer
        );
        ok(
            call(
                "PATCH",
                "/api/customers/" + customer + "/follow-records/" + id + "/review",
                manager,
                Map.of(
                    "version",
                    cv,
                    "reviewStatus",
                    "CONFIRMED",
                    "reviewReason",
                    "已核对本阶段事实"
                )
            )
        );
    }

    /** 提交签署进度测试请求。 */
    private ResponseEntity<JsonNode> updateProgress(Long actor, String target, String reason) {
        Long version = jdbc.query(
            "SELECT version FROM crm_contract WHERE id=?",
            resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
            contract
        );
        assertThat(version)
            .as("contract %s must remain present before %s", contract, target)
            .isNotNull();
        return call(
            "PATCH",
            "/api/contracts/" + contract + "/signing-progress",
            actor,
            Map.of("version", version, "targetProgress", target, "reason", reason)
        );
    }

    /** 读取最新签署进度供断言。 */
    private JsonNode progress(Long actor, String target, String reason) {
        return ok(updateProgress(actor, target, reason));
    }

    /** 验证登记列表仅包含成交商机并保留归属和权限范围。 */
    @Test
    void registrationListIncludesOnlyDealAndKeepsOwnershipAndScope() {
        Long colleague = user("colleague", "SPECIALIST", team);
        extraUsers.add(colleague);
        jdbc.update(
            "INSERT INTO sys_team(team_code,team_name) VALUES(?,?)",
            prefix + "other",
            prefix + "other"
        );
        otherTeam = jdbc.queryForObject(
            "SELECT id FROM sys_team WHERE team_code=?",
            Long.class,
            prefix + "other"
        );
        Long outside = user("outside", "MANAGER", otherTeam);
        extraUsers.add(outside);
        jdbc.update(
            "INSERT INTO crm_opportunity_collaborator(opportunity_id,user_id,split_ratio) VALUES(?,?,10)",
            opportunity,
            colleague
        );
        Long newOpportunity = createOpportunity();
        for (Long actor : List.of(sales, manager, admin)) {
            JsonNode result = ok(
                call(
                    "GET",
                    "/api/contracts/registration-opportunities?keyword=" + prefix,
                    actor,
                    null
                )
            );
            assertThat(result.path("total").asInt()).isEqualTo(1);
            JsonNode row = result.path("list").get(0);
            assertThat(row.path("opportunityId").asLong()).isEqualTo(opportunity);
            assertThat(row.path("contractId").asLong(0)).isZero();
            assertThat(row.path("ownerName").asText()).isEqualTo(prefix + "sales");
            assertThat(row.path("teamName").asText()).isEqualTo(prefix);
            assertThat(row.path("canUpload").asBoolean()).isEqualTo(!actor.equals(manager));
            JsonNode customerRow = ok(call("GET", "/api/customers/" + customer, actor, null));
            assertThat(customerRow.path("ownerName").asText()).isEqualTo(prefix + "sales");
            assertThat(customerRow.path("teamName").asText()).isEqualTo(prefix);
        }
        for (Long actor : List.of(colleague, outside)) {
            assertThat(
                ok(
                    call(
                        "GET",
                        "/api/contracts/registration-opportunities?keyword=" + prefix,
                        actor,
                        null
                    )
                )
                    .path("total")
                    .asInt()
            ).isZero();
            assertThat(
                call(
                    "POST",
                    "/api/contracts/from-opportunity",
                    actor,
                    Map.of("opportunityId", opportunity)
                ).getStatusCode()
            ).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(
            call(
                "POST",
                "/api/contracts/from-opportunity",
                sales,
                Map.of("opportunityId", newOpportunity)
            ).getStatusCode()
        ).isEqualTo(HttpStatus.BAD_REQUEST);
        contract = ok(
            call(
                "POST",
                "/api/contracts/from-opportunity",
                sales,
                Map.of("opportunityId", opportunity)
            )
        ).asLong();
        assertThat(
            call(
                "POST",
                "/api/contracts/from-opportunity",
                sales,
                Map.of("opportunityId", opportunity)
            ).getStatusCode()
        ).isEqualTo(HttpStatus.CONFLICT);
        JsonNode registered = ok(
            call(
                "GET",
                "/api/contracts/registration-opportunities?keyword=" +
                    prefix +
                    "&contractStatus=DRAFT",
                manager,
                null
            )
        );
        assertThat(registered.path("list").get(0).path("contractId").asLong()).isEqualTo(contract);
        assertThat(
            ok(
                call(
                    "GET",
                    "/api/contracts/registration-opportunities?keyword=" +
                        prefix +
                        "&contractStatus=UNREGISTERED",
                    sales,
                    null
                )
            )
                .path("total")
                .asInt()
        ).isZero();
    }

    /** 创建测试用成交或指定阶段商机。 */
    Long createOpportunity() {
        return ok(
            call(
                "POST",
                "/api/opportunities",
                admin,
                Map.of(
                    "customerId",
                    customer,
                    "opportunityName",
                    prefix + "商机",
                    "expectedAmount",
                    1000,
                    "expectedCloseDate",
                    "2026-12-01"
                )
            )
        ).asLong();
    }

    /** 提交测试阶段修正请求。 */
    void stage(String target, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put(
            "version",
            jdbc.queryForObject(
                "SELECT version FROM crm_opportunity WHERE id=?",
                Long.class,
                opportunity
            )
        );
        body.put("targetStage", target);
        if (reason != null) body.put("reason", reason);
        ok(call("PATCH", "/api/opportunities/" + opportunity + "/stage", sales, body));
    }

    /** 提交测试报价或合作确认证据。 */
    void evidence(String kind, String description) {
        ok(
            call(
                "POST",
                "/api/opportunities/" + opportunity + "/evidence",
                sales,
                Map.of(
                    "version",
                    jdbc.queryForObject(
                        "SELECT version FROM crm_opportunity WHERE id=?",
                        Long.class,
                        opportunity
                    ),
                    "kind",
                    kind,
                    "quoteAmount",
                    1000,
                    "description",
                    description
                )
            )
        );
    }

    /** 创建测试用账号。 */
    Long user(String name, String role, Long teamId) {
        jdbc.update(
            "INSERT INTO sys_user(username,password_hash,real_name,role_code,team_id,account_status,must_change_password) VALUES(?,?,?,?,?,'ACTIVE',0)",
            prefix + name,
            passwords.encode("ContractFixture123!"),
            prefix + name,
            role,
            teamId
        );
        return jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE username=?",
            Long.class,
            prefix + name
        );
    }

    /** 取得测试账号的有效登录令牌。 */
    String token(Long id) {
        return tokens.issue(AuthPrincipal.from(users.findWithTeamById(id))).value();
    }

    /** 发送带身份信息的测试 HTTP 请求。 */
    ResponseEntity<JsonNode> call(String method, String url, Long actor, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token(actor));
        return http.exchange(
            url,
            HttpMethod.valueOf(method),
            new HttpEntity<>(body, headers),
            JsonNode.class
        );
    }

    /** 断言接口成功并读取返回数据。 */
    JsonNode ok(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode())
            .withFailMessage("%s", response.getBody())
            .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("code").asInt()).isZero();
        return response.getBody().path("data");
    }
}
