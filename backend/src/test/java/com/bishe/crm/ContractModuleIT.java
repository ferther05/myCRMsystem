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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class ContractModuleIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    @Autowired SystemUserMapper users;
    @Autowired PasswordEncoder passwords;
    String prefix; Long team; Long manager; Long sales; Long admin; Long customer; Long opportunity; Long contract;
    List<Long> extraUsers = new ArrayList<>();
    List<Long> extraContracts = new ArrayList<>();
    Long otherTeam;

    @BeforeEach void setup() {
        prefix = "contract-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO sys_team(team_code,team_name) VALUES(?,?)", prefix, prefix);
        team = jdbc.queryForObject("SELECT id FROM sys_team WHERE team_code=?", Long.class, prefix);
        manager = user("manager", "MANAGER", team); sales = user("sales", "SPECIALIST", team); admin = user("admin", "ADMIN", null); extraUsers.add(admin);
        jdbc.update("INSERT INTO crm_customer(customer_name,normalized_name,owner_id,team_id,customer_status,sea_type,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?)", prefix, prefix, sales, team, "FOLLOWING", "PRIVATE", manager, manager);
        customer = jdbc.queryForObject("SELECT id FROM crm_customer WHERE customer_name=?", Long.class, prefix);
        jdbc.update("INSERT INTO crm_contact(customer_id,contact_name,phone,is_primary) VALUES(?,'合同联系人','13900000000',1)", customer);
        opportunity = createOpportunity();
        java.time.LocalDateTime now=java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        JsonNode d=ok(call("GET","/api/opportunities/"+opportunity,sales,null));
        ok(call("POST","/api/opportunities/"+opportunity+"/follow-records",sales,Map.of("customerVersion",d.path("customerVersion").asLong(),
                "contactId",d.path("contacts").get(0).path("id").asLong(),"followMethod","PHONE","content","已沟通合同需求及签署计划",
                "resultCode","NEED_CONFIRMED","substantiveResult","采购范围与签署安排已确认","nextAction","发送报价",
                "nextActionAt",now.plusDays(1).toString(),"followedAt",now.minusSeconds(1).toString())));
        evidence("QUOTE", "正式报价"); evidence("CONFIRMATION", "客户确认合作");
        assertThat(ok(call("GET","/api/opportunities/"+opportunity,sales,null)).path("stageCode").asText()).isEqualTo("DEAL");
    }

    @AfterEach void cleanup() {
        if (opportunity != null) jdbc.update("DELETE FROM crm_opportunity_collaborator WHERE opportunity_id=?", opportunity);
        if (contract != null) {
            for (String path : jdbc.queryForList("SELECT storage_path FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=?", String.class, contract)) { try { Files.deleteIfExists(Path.of(path)); } catch (Exception ignored) { } }
            jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='CONTRACT' AND biz_id=?", contract);
            jdbc.update("DELETE FROM crm_contract_signing_history WHERE contract_id=?", contract);
            jdbc.update("DELETE FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=?", contract);
            jdbc.update("DELETE FROM crm_contract WHERE id=?", contract);
        }
        for (Long id : extraContracts) {
            jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='CONTRACT' AND biz_id=?", id);
            jdbc.update("DELETE FROM crm_contract_signing_history WHERE contract_id=?", id);
            jdbc.update("DELETE FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=?", id);
            jdbc.update("DELETE FROM crm_contract WHERE id=?", id);
        }
        if (opportunity != null) {
            List<Long> fixtureContracts = jdbc.queryForList("SELECT id FROM crm_contract WHERE opportunity_id=?", Long.class, opportunity);
            for (Long id : fixtureContracts) {
                for (String path : jdbc.queryForList("SELECT storage_path FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=?", String.class, id)) { try { Files.deleteIfExists(Path.of(path)); } catch (Exception ignored) { } }
                jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='CONTRACT' AND biz_id=?", id);
                jdbc.update("DELETE FROM crm_contract_signing_history WHERE contract_id=?", id);
                jdbc.update("DELETE FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=?", id);
                jdbc.update("DELETE FROM crm_contract WHERE id=?", id);
            }
        }
        if (customer != null) {
            jdbc.update("DELETE l FROM crm_operation_log l JOIN crm_opportunity o ON l.biz_type='OPPORTUNITY' AND l.biz_id=o.id WHERE o.customer_id=?", customer);
            jdbc.update("DELETE l FROM crm_opportunity_stage_log l JOIN crm_opportunity o ON o.id=l.opportunity_id WHERE o.customer_id=?", customer);
            jdbc.update("DELETE FROM crm_follow_record WHERE customer_id=?", customer);
            jdbc.update("DELETE FROM crm_opportunity WHERE customer_id=?", customer);
        }
        if (customer != null) { jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=?", customer); jdbc.update("DELETE FROM crm_follow_record WHERE customer_id=?", customer); jdbc.update("DELETE FROM crm_contact WHERE customer_id=?", customer); jdbc.update("DELETE FROM crm_customer WHERE id=?", customer); }
        if (sales != null) jdbc.update("DELETE FROM sys_user WHERE id=?", sales); if (manager != null) jdbc.update("DELETE FROM sys_user WHERE id=?", manager);
        for (Long id : extraUsers) jdbc.update("DELETE FROM sys_user WHERE id=?", id);
        if (team != null) jdbc.update("DELETE FROM sys_team WHERE id=?", team);
        if (otherTeam != null) jdbc.update("DELETE FROM sys_team WHERE id=?", otherTeam);
    }

    @Test void registrationUploadReviewOpensWonGateAndPreservesVersion() {
        ResponseEntity<JsonNode> created = call("POST", "/api/contracts/from-opportunity", sales, Map.of("opportunityId", opportunity));
        assertThat(created.getStatusCode()).withFailMessage("%s", created.getBody()).isEqualTo(HttpStatus.OK); contract = created.getBody().path("data").asLong();
        JsonNode draft = ok(call("GET", "/api/contracts/" + contract, sales, null)); assertThat(draft.path("contractStatus").asText()).isEqualTo("DRAFT");
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("materialType", "SIGNED_CONTRACT"); multipart.part("note", "测试签署文件");
        multipart.part("file", new ByteArrayResource("signed-contract".getBytes()) { @Override public String getFilename() { return "contract.pdf"; } }, MediaType.APPLICATION_PDF);
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token(sales)); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<JsonNode> upload = http.exchange("/api/contracts/" + contract + "/attachments", HttpMethod.POST, new HttpEntity<>(multipart.build(), headers), JsonNode.class);
        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> withdrawalWithFile = updateProgress(sales, "WITHDRAWN", "已有双方签署文件时不能直接撤回");
        assertThat(withdrawalWithFile.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<JsonNode> incomplete=call("POST","/api/contracts/"+contract+"/submit",sales,Map.of("version",draft.path("version").asLong()));
        assertThat(incomplete.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(incomplete.getBody().path("code").asInt()).isEqualTo(40017);
        ok(call("PUT", "/api/contracts/" + contract, sales, Map.of("version", draft.path("version").asLong(), "contractName", "实际软件服务合同", "externalContractNo", "HT-TEST-001", "businessType", "软件开发", "ourPartyName", "我方主体", "counterpartyName", "客户主体", "contractAmount", 1200.50, "signedAt", "2026-10-20", "description", "以实际签署文件为准")));
        progress(sales, "WAIT_OUR_SIGN", "我方完成内部审批，提交签署");
        progress(manager, "WAIT_COUNTERPARTY_SIGN", "我方已签，交由对方签署");
        JsonNode current = ok(call("GET", "/api/contracts/" + contract, sales, null)); ok(call("POST", "/api/contracts/" + contract + "/submit", sales, Map.of("version", current.path("version").asLong())));
        JsonNode pending = ok(call("GET", "/api/contracts/" + contract, manager, null));
        assertThat(pending.path("contractStatus").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(pending.path("signingProgress").asText()).isEqualTo("WAIT_COUNTERPARTY_SIGN");
        ok(call("POST", "/api/contracts/" + contract + "/review", manager, Map.of("version", pending.path("version").asLong(), "pass", false, "opinion", "请补传清晰的双方盖章页")));
        JsonNode returned = ok(call("GET", "/api/contracts/" + contract, sales, null));
        assertThat(returned.path("contractStatus").asText()).isEqualTo("RETURNED");
        assertThat(returned.path("signingProgress").asText()).isEqualTo("WAIT_COUNTERPARTY_SIGN");
        MultipartBodyBuilder revisedMultipart = new MultipartBodyBuilder();
        revisedMultipart.part("materialType", "SIGNED_CONTRACT"); revisedMultipart.part("note", "补充清晰签署页");
        revisedMultipart.part("file", new ByteArrayResource("revised-signed-contract".getBytes()) { @Override public String getFilename() { return "contract-revised.pdf"; } }, MediaType.APPLICATION_PDF);
        HttpHeaders revisedHeaders = new HttpHeaders(); revisedHeaders.setBearerAuth(token(sales)); revisedHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<JsonNode> revisedUpload = http.exchange("/api/contracts/" + contract + "/attachments", HttpMethod.POST, new HttpEntity<>(revisedMultipart.build(), revisedHeaders), JsonNode.class);
        assertThat(revisedUpload.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode resubmitting = ok(call("GET", "/api/contracts/" + contract, sales, null));
        ok(call("POST", "/api/contracts/" + contract + "/submit", sales, Map.of("version", resubmitting.path("version").asLong())));
        JsonNode resubmitted = ok(call("GET", "/api/contracts/" + contract, manager, null));
        ok(call("POST", "/api/contracts/" + contract + "/review", manager, Map.of("version", resubmitted.path("version").asLong(), "pass", true, "opinion", "补充资料与登记信息已核对")));
        JsonNode reviewed = ok(call("GET", "/api/contracts/" + contract, manager, null)); assertThat(reviewed.path("contractStatus").asText()).isEqualTo("SIGNED");
        JsonNode won=ok(call("GET","/api/opportunities/"+opportunity,sales,null));
        assertThat(won.path("stageCode").asText()).isEqualTo("WON");
        assertThat(won.path("stageHistory").get(0).path("actionCode").asText()).isEqualTo("AUTO_CONTRACT_REVIEW");
        assertThat(won.path("stageHistory").get(0).path("operatorName").asText()).isEqualTo("系统自动");
        assertThat(won.path("stageProgress").get(3).path("ready").asBoolean()).isTrue();
        assertThat(reviewed.path("signedAt").asText()).isEqualTo("2026-10-20");
        assertThat(reviewed.path("signingProgress").asText()).isEqualTo("BOTH_SIGNED");
        assertThat(reviewed.path("signingHistory").size()).isEqualTo(4);
        assertThat(ok(call("GET", "/api/contracts/registration-opportunities?keyword=" + prefix, sales, null)).path("total").asInt()).isZero();
        assertThat(ok(call("GET", "/api/contracts?keyword=" + prefix, sales, null)).path("list").get(0).path("id").asLong()).isEqualTo(contract);
    }

    @Test void signingProgressEnforcesSequenceScopeCorrectionAndWithdrawalTerminal() {
        contract = ok(call("POST", "/api/contracts/from-opportunity", sales, Map.of("opportunityId", opportunity))).asLong();
        Long peer = user("peer", "SPECIALIST", team); extraUsers.add(peer);
        jdbc.update("INSERT INTO sys_team(team_code,team_name) VALUES(?,?)", prefix + "outside", prefix + "outside");
        otherTeam = jdbc.queryForObject("SELECT id FROM sys_team WHERE team_code=?", Long.class, prefix + "outside");
        Long outsideManager = user("outside-manager", "MANAGER", otherTeam); extraUsers.add(outsideManager);
        assertThat(updateProgress(peer, "WAIT_OUR_SIGN", "非负责人专员无权修改").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(updateProgress(outsideManager, "WAIT_OUR_SIGN", "其他团队经理无权修改").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<JsonNode> skip = updateProgress(sales, "WAIT_COUNTERPARTY_SIGN", "跳过我方签署");
        assertThat(skip.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ok(updateProgress(manager, "WAIT_OUR_SIGN", "经理确认进入我方签署"));
        assertThat(updateProgress(sales, "BOTH_SIGNED", "不能手动标记双方已签署").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ok(updateProgress(sales, "EXCEPTION", "签署主体信息待补充"));
        ResponseEntity<JsonNode> invalidRecovery = updateProgress(manager, "WAIT_OUR_SIGN", "不允许跳过异常恢复节点");
        assertThat(invalidRecovery.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ok(updateProgress(manager, "INTERNAL_HANDLING", "资料已补正，返回内部办理"));

        JsonNode current = ok(call("GET", "/api/contracts/" + contract, admin, null));
        ok(call("PATCH", "/api/contracts/" + contract + "/signing-progress", admin, Map.of("version", current.path("version").asLong(), "targetProgress", "WAIT_COUNTERPARTY_SIGN", "reason", "管理员修正错误阶段")));
        JsonNode corrected = ok(call("GET", "/api/contracts/" + contract, admin, null));
        assertThat(corrected.path("signingHistory").get(0).path("correction").asBoolean()).isTrue();

        ok(updateProgress(admin, "WITHDRAWN", "双方决定终止签署"));
        JsonNode withdrawn = ok(call("GET", "/api/contracts/" + contract, admin, null));
        assertThat(withdrawn.path("contractStatus").asText()).isEqualTo("VOID");
        assertThat(withdrawn.path("signingProgress").asText()).isEqualTo("WITHDRAWN");
        ResponseEntity<JsonNode> terminal = updateProgress(admin, "INTERNAL_HANDLING", "不得恢复撤回登记");
        assertThat(terminal.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Long replacement = ok(call("POST", "/api/contracts/from-opportunity", sales, Map.of("opportunityId", opportunity))).asLong();
        extraContracts.add(replacement);
        assertThat(replacement).isNotEqualTo(contract);
    }

    private ResponseEntity<JsonNode> updateProgress(Long actor, String target, String reason) {
        Long version = jdbc.query("SELECT version FROM crm_contract WHERE id=?", resultSet -> resultSet.next() ? resultSet.getLong(1) : null, contract);
        assertThat(version).as("contract %s must remain present before %s", contract, target).isNotNull();
        return call("PATCH", "/api/contracts/" + contract + "/signing-progress", actor,
                Map.of("version", version, "targetProgress", target, "reason", reason));
    }

    private JsonNode progress(Long actor, String target, String reason) {
        return ok(updateProgress(actor, target, reason));
    }

    @Test void registrationListIncludesOnlyDealAndKeepsOwnershipAndScope() {
        Long colleague = user("colleague", "SPECIALIST", team); extraUsers.add(colleague);
        jdbc.update("INSERT INTO sys_team(team_code,team_name) VALUES(?,?)", prefix + "other", prefix + "other");
        otherTeam = jdbc.queryForObject("SELECT id FROM sys_team WHERE team_code=?", Long.class, prefix + "other");
        Long outside = user("outside", "MANAGER", otherTeam); extraUsers.add(outside);
        jdbc.update("INSERT INTO crm_opportunity_collaborator(opportunity_id,user_id,split_ratio) VALUES(?,?,10)", opportunity, colleague);
        Long newOpportunity = createOpportunity();
        for (Long actor : List.of(sales, manager, admin)) {
            JsonNode result = ok(call("GET", "/api/contracts/registration-opportunities?keyword=" + prefix, actor, null));
            assertThat(result.path("total").asInt()).isEqualTo(1);
            JsonNode row = result.path("list").get(0);
            assertThat(row.path("opportunityId").asLong()).isEqualTo(opportunity);
            assertThat(row.path("contractId").asLong(0)).isZero();
            assertThat(row.path("ownerName").asText()).isEqualTo(prefix + "sales");
            assertThat(row.path("teamName").asText()).isEqualTo(prefix);
            assertThat(row.path("canUpload").asBoolean()).isTrue();
            JsonNode customerRow = ok(call("GET", "/api/customers/" + customer, actor, null));
            assertThat(customerRow.path("ownerName").asText()).isEqualTo(prefix + "sales");
            assertThat(customerRow.path("teamName").asText()).isEqualTo(prefix);
        }
        for (Long actor : List.of(colleague, outside)) {
            assertThat(ok(call("GET", "/api/contracts/registration-opportunities?keyword=" + prefix, actor, null)).path("total").asInt()).isZero();
            assertThat(call("POST", "/api/contracts/from-opportunity", actor, Map.of("opportunityId", opportunity)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(call("POST", "/api/contracts/from-opportunity", sales, Map.of("opportunityId", newOpportunity)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        contract = ok(call("POST", "/api/contracts/from-opportunity", sales, Map.of("opportunityId", opportunity))).asLong();
        assertThat(call("POST", "/api/contracts/from-opportunity", sales, Map.of("opportunityId", opportunity)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode registered = ok(call("GET", "/api/contracts/registration-opportunities?keyword=" + prefix + "&contractStatus=DRAFT", manager, null));
        assertThat(registered.path("list").get(0).path("contractId").asLong()).isEqualTo(contract);
        assertThat(ok(call("GET", "/api/contracts/registration-opportunities?keyword=" + prefix + "&contractStatus=UNREGISTERED", sales, null)).path("total").asInt()).isZero();
    }

    Long createOpportunity() { return ok(call("POST", "/api/opportunities", admin, Map.of("customerId", customer, "opportunityName", prefix + "商机", "expectedAmount", 1000, "expectedCloseDate", "2026-12-01"))).asLong(); }
    void stage(String target, String reason) { Map<String,Object> body = new HashMap<>(); body.put("version", jdbc.queryForObject("SELECT version FROM crm_opportunity WHERE id=?", Long.class, opportunity)); body.put("targetStage", target); if (reason != null) body.put("reason", reason); ok(call("PATCH", "/api/opportunities/" + opportunity + "/stage", sales, body)); }
    void evidence(String kind, String description) { ok(call("POST", "/api/opportunities/" + opportunity + "/evidence", sales, Map.of("version", jdbc.queryForObject("SELECT version FROM crm_opportunity WHERE id=?", Long.class, opportunity), "kind", kind, "quoteAmount", 1000, "description", description))); }
    Long user(String name, String role, Long teamId) { jdbc.update("INSERT INTO sys_user(username,password_hash,real_name,role_code,team_id,account_status,must_change_password) VALUES(?,?,?,?,?,'ACTIVE',0)", prefix + name, passwords.encode("ContractFixture123!"), prefix + name, role, teamId); return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, prefix + name); }
    String token(Long id) { return tokens.issue(AuthPrincipal.from(users.findWithTeamById(id))).value(); }
    ResponseEntity<JsonNode> call(String method, String url, Long actor, Object body) { HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON); headers.setBearerAuth(token(actor)); return http.exchange(url, HttpMethod.valueOf(method), new HttpEntity<>(body, headers), JsonNode.class); }
    JsonNode ok(ResponseEntity<JsonNode> response) { assertThat(response.getStatusCode()).withFailMessage("%s", response.getBody()).isEqualTo(HttpStatus.OK); assertThat(response.getBody().path("code").asInt()).isZero(); return response.getBody().path("data"); }
}
