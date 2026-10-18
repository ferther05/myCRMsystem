package com.bishe.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.auth.security.JwtTokenService;
import com.bishe.crm.module.system.mapper.AccountMapper;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
class AccountManagementIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtTokenService tokens;
    @Autowired SystemUserMapper users;
    @MockitoSpyBean AccountMapper accounts;
    String prefix;
    Long east, south, admin, manager, specialist, colleague, outsider;
    final List<Long> ids = new ArrayList<>();
    final List<Long> customers = new ArrayList<>();
    static final String PASSWORD = "AccountFixture123!";

    @BeforeEach void setup() {
        prefix = "am-" + UUID.randomUUID().toString().substring(0, 10);
        east = team("east"); south = team("south");
        admin = user("admin", "ADMIN", null);
        manager = user("manager", "MANAGER", east);
        specialist = user("sales", "SPECIALIST", east);
        colleague = user("other", "SPECIALIST", east);
        outsider = user("south", "SPECIALIST", south);
        jdbc.update("UPDATE sys_team SET manager_id=? WHERE id=?", manager, east);
    }

    @AfterEach void cleanup() {
        reset(accounts);
        // Cleanup only this test's unique prefix/ids. Seed users and user-created data are untouched.
        ids.clear();
        ids.addAll(jdbc.queryForList("SELECT id FROM sys_user WHERE username LIKE ?", Long.class, prefix + "%"));
        for (Long id : customers) {
            jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=?", id);
            jdbc.update("DELETE FROM crm_customer WHERE id=?", id);
        }
        for (Long id : ids) {
            jdbc.update("DELETE FROM crm_operation_log WHERE operator_id=? OR (biz_type='USER' AND biz_id=?)", id, id);
            jdbc.update("DELETE FROM crm_approval WHERE applicant_id=? OR approver_id=? OR (biz_type='USER' AND biz_id=?)", id,id,id);
        }
        jdbc.update("UPDATE sys_team SET manager_id=NULL WHERE id IN (?,?)", east,south);
        for (Long id : ids) jdbc.update("DELETE FROM sys_user WHERE id=?", id);
        jdbc.update("DELETE FROM sys_team WHERE id IN (?,?)", east,south);
    }

    @Test void realUserListsDetailsCountsAndTeamsAreScoped() {
        String sales = token(specialist), mgr = token(manager), root = token(admin);
        var own = ok(call("GET", "/api/users?size=1", sales, null));
        assertThat(own.path("total").asLong()).isEqualTo(1);
        assertThat(own.path("list").get(0).path("id").asLong()).isEqualTo(specialist);
        assertThat(ok(call("GET", "/api/users?size=1", mgr, null)).path("total").asLong()).isEqualTo(3);
        assertThat(ok(call("GET", "/api/users?keyword="+prefix, root, null)).path("total").asLong()).isEqualTo(5);
        status(call("GET", "/api/users/"+outsider, mgr, null), 404);
        status(call("GET", "/api/users/"+colleague, sales, null), 404);
        status(call("GET", "/api/users/"+specialist, mgr, null), 200);
        assertThat(ok(call("GET", "/api/teams", mgr, null)).size()).isEqualTo(1);
        assertThat(own.toString()).doesNotContain("passwordHash", "password_hash");
        status(call("GET", "/api/users?page=0", sales, null), 400);
        status(call("GET", "/api/users", null, null), 401);
    }

    @Test void profileIsSelfOnlyWhitelistedAndRefreshesSessionAtomically() {
        String old = token(specialist);
        var result = ok(call("PUT", "/api/account/profile", old, Map.of("realName","更新姓名", "phone","13812345678",
                "email",prefix+"@example.test", "roleCode","ADMIN", "teamId",south,"id",outsider)));
        String next = result.path("accessToken").asText();
        assertThat(next).isNotEmpty();
        status(call("GET", "/api/auth/me", old, null), 401);
        var me = ok(call("GET", "/api/auth/me", next, null));
        assertThat(me.path("realName").asText()).isEqualTo("更新姓名");
        assertThat(me.path("roleCode").asText()).isEqualTo("SPECIALIST");
        assertThat(me.path("teamId").asLong()).isEqualTo(east);
        assertThat(me.path("email").asText()).isEqualTo(prefix+"@example.test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_operation_log WHERE biz_id=? AND action_code='PROFILE_UPDATED'",Long.class,specialist)).isEqualTo(1);
        status(call("PUT", "/api/account/profile", next, Map.of("realName","姓名", "email","bad")),400);
        status(call("PUT", "/api/account/profile", next, Map.of("realName","姓名", "phone","中文电话")),400);
    }

    @Test void managerApplicationRejectResubmitApproveAndFirstPasswordChangeAreReal() {
        String mgr = token(manager), root = token(admin);
        String username = prefix + "-new";
        var request = Map.of("username",username,"realName","待开通专员", "reason","团队新增成员", "teamId",south,"roleCode","ADMIN");
        long application = ok(call("POST", "/api/account-applications", mgr, request)).asLong();
        Long user = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,username);
        assertThat(jdbc.queryForObject("SELECT team_id FROM sys_user WHERE id=?",Long.class,user)).isEqualTo(east);
        assertThat(jdbc.queryForObject("SELECT role_code FROM sys_user WHERE id=?",String.class,user)).isEqualTo("SPECIALIST");
        status(login(username,PASSWORD),401);
        status(call("POST", "/api/account-applications/"+application+"/review",mgr,Map.of("version",0,"approved",true,"opinion","越权","initialPassword",PASSWORD)),403);
        status(call("GET", "/api/account-applications",token(specialist),null),403);
        ok(call("POST", "/api/account-applications/"+application+"/review",root,Map.of("version",0,"approved",false,"opinion","请核对资料")));
        status(login(username,PASSWORD),401);
        ok(call("POST", "/api/account-applications/"+application+"/resubmit?version=1",mgr,null));
        ok(call("POST", "/api/account-applications/"+application+"/review",root,Map.of("version",2,"approved",true,"opinion","核对通过","initialPassword",PASSWORD)));
        var session = ok(login(username,PASSWORD));
        assertThat(session.path("user").path("mustChangePassword").asBoolean()).isTrue();
        status(call("GET", "/api/users",session.path("accessToken").asText(),null),403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_operation_log WHERE biz_id=?",Long.class,user)).isEqualTo(4);
        status(call("POST", "/api/account-applications",mgr,request),409);
        assertThat(ok(call("GET", "/api/account-applications",token(outsiderManager()),null)).path("total").asInt()).isZero();
    }

    @Test void administratorCreationResetDisableEnableAndRoleChangesRevokeOldTokens() {
        String root = token(admin), old = token(specialist);
        var request = Map.of("username",prefix+"-created","realName","管理员建号", "roleCode","SPECIALIST","teamId",east,"initialPassword",PASSWORD);
        status(call("POST", "/api/users",token(manager),request),403);
        var created = ok(call("POST", "/api/users",root,request));
        assertThat(created.path("mustChangePassword").asBoolean()).isTrue();
        status(call("POST", "/api/users",root,request),409);
        status(call("POST", "/api/users",root,Map.of("username",prefix+"-bad","realName","无团队","roleCode","SPECIALIST","initialPassword",PASSWORD)),400);
        ok(call("PUT", "/api/users/"+specialist+"/access",root,Map.of("version",0,"roleCode","SPECIALIST","teamId",south,"accountStatus","ACTIVE")));
        status(call("GET", "/api/auth/me",old,null),401);
        String moved = token(specialist);
        assertThat(ok(call("GET", "/api/teams",moved,null)).get(0).path("id").asLong()).isEqualTo(south);
        ok(call("POST", "/api/users/"+specialist+"/reset-password",root,Map.of("version",1,"initialPassword","ResetFixture456!")));
        status(call("GET", "/api/auth/me",moved,null),401);
        status(login(prefix+"-sales",PASSWORD),401);
        var resetLogin = ok(login(prefix+"-sales","ResetFixture456!"));
        assertThat(resetLogin.path("user").path("mustChangePassword").asBoolean()).isTrue();
        long version = jdbc.queryForObject("SELECT version FROM sys_user WHERE id=?",Long.class,specialist);
        ok(call("PUT", "/api/users/"+specialist+"/access",root,Map.of("version",version,"roleCode","SPECIALIST","teamId",south,"accountStatus","DISABLED")));
        status(login(prefix+"-sales","ResetFixture456!"),401);
        ok(call("PUT", "/api/users/"+specialist+"/access",root,Map.of("version",version+1,"roleCode","SPECIALIST","teamId",south,"accountStatus","ACTIVE")));
        status(login(prefix+"-sales","ResetFixture456!"),200);
        status(call("PUT", "/api/users/"+admin+"/access",root,Map.of("version",0,"roleCode","ADMIN","accountStatus","DISABLED")),400);
    }

    @Test void approvalConcurrentSubmissionOnlyCommitsOnce() throws Exception {
        String root = token(admin);
        long id = ok(call("POST", "/api/account-applications",token(manager),Map.of("username",prefix+"-race","realName","并发申请","reason","并发审核测试"))).asLong();
        var body = Map.of("version",0,"approved",true,"opinion","核准","initialPassword",PASSWORD);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i=0;i<2;i++) futures.add(pool.submit(() -> { start.await(); return call("POST","/api/account-applications/"+id+"/review",root,body).getStatusCode().value(); }));
            start.countDown();
            assertThat(List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_operation_log WHERE action_code='ACCOUNT_APPROVED' AND operator_id=?",Long.class,admin)).isEqualTo(1);
    }

    @Test void managerDowngradeRevokesOldTokenAndRemovesManagementOperations() {
        String root=token(admin), old=token(manager);
        ok(call("PUT","/api/users/"+manager+"/access",root,Map.of("version",0,"roleCode","SPECIALIST","teamId",east,"accountStatus","ACTIVE")));
        status(call("GET","/api/account-applications",old,null),401);
        String downgraded=token(manager);
        assertThat(ok(call("GET","/api/users",downgraded,null)).path("total").asInt()).isEqualTo(1);
        status(call("GET","/api/account-applications",downgraded,null),403);
        status(call("POST","/api/users/"+specialist+"/reset-password",downgraded,Map.of("version",0,"initialPassword","ResetFixture456!")),403);
        assertThat(jdbc.queryForObject("SELECT manager_id IS NULL FROM sys_team WHERE id=?",Boolean.class,east)).isTrue();
    }

    @Test void accountWritesRollbackWhenRequiredAuditFails() {
        String root = token(admin), username = prefix+"-rollback";
        doThrow(new DataIntegrityViolationException("Injected fixture audit failure"))
                .when(accounts).audit(anyLong(),eq("ACCOUNT_CREATED"),anyLong(),isNull(),any(),anyString());
        status(call("POST", "/api/users",root,Map.of("username",username,"realName","回滚测试","roleCode","SPECIALIST","teamId",east,"initialPassword",PASSWORD)),409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username=?",Long.class,username)).isZero();
    }

    @Test void customerListDetailHandoverExpiryPublicMaskAndOwnershipGuardUseDatabaseScope() {
        long owned = customer("own",specialist,east,"PRIVATE");
        long peer = customer("peer",colleague,east,"PRIVATE");
        long foreign = customer("foreign",outsider,south,"PRIVATE");
        long pub = customer("public",null,null,"PUBLIC");
        String sales = token(specialist), mgr = token(manager), root = token(admin);
        assertThat(ok(call("GET","/api/customers?size=1",sales,null)).path("total").asInt()).isEqualTo(1);
        assertThat(ok(call("GET","/api/customers",mgr,null)).path("total").asInt()).isEqualTo(2);
        status(call("GET","/api/customers/"+foreign,mgr,null),404);
        status(call("GET","/api/customers/"+peer,sales,null),404);
        status(call("GET","/api/customers/"+foreign,root,null),200);
        var publicData = ok(call("GET","/api/customers/"+pub,sales,null));
        assertThat(publicData.path("primaryPhone").asText()).isEqualTo("138****5678");
        assertThat(publicData.toString()).doesNotContain("13812345678","unifiedSocialCreditCode","password","attachment");
        jdbc.update("INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,old_owner_id,ownership_seq,handover_readonly_until) VALUES('CUSTOMER',?,'HANDOVER',?,?,0,DATE_ADD(NOW(3),INTERVAL 7 DAY))",foreign,admin,specialist);
        assertThat(ok(call("GET","/api/customers/"+foreign,sales,null)).path("readonly").asBoolean()).isTrue();
        jdbc.update("UPDATE crm_operation_log SET handover_readonly_until=DATE_SUB(NOW(3),INTERVAL 1 SECOND) WHERE biz_type='CUSTOMER' AND biz_id=?",foreign);
        status(call("GET","/api/customers/"+foreign,sales,null),404);
        jdbc.update("UPDATE crm_operation_log SET handover_readonly_until=DATE_ADD(NOW(3),INTERVAL 7 DAY),ownership_seq=999 WHERE biz_type='CUSTOMER' AND biz_id=?",foreign);
        status(call("GET","/api/customers/"+foreign,sales,null),404);
        status(call("PUT","/api/users/"+specialist+"/access",root,Map.of("version",0,"roleCode","SPECIALIST","teamId",south,"accountStatus","ACTIVE")),409);
        status(call("POST","/api/customers/"+owned,sales,Map.of()),405);
    }

    private Long team(String suffix) {
        jdbc.update("INSERT INTO sys_team(team_code,team_name) VALUES(?,?)",prefix+suffix,prefix+suffix);
        return jdbc.queryForObject("SELECT id FROM sys_team WHERE team_code=?",Long.class,prefix+suffix);
    }
    private Long user(String suffix,String role,Long team) {
        jdbc.update("INSERT INTO sys_user(username,password_hash,real_name,role_code,team_id,must_change_password) VALUES(?,?,?,?,?,0)",prefix+"-"+suffix,passwords.encode(PASSWORD),suffix,role,team);
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,prefix+"-"+suffix);
    }
    private Long outsiderManager() { return user("south-manager","MANAGER",south); }
    private String token(Long id) { return tokens.issue(AuthPrincipal.from(users.findWithTeamById(id))).value(); }
    private ResponseEntity<JsonNode> login(String name,String password) { return call("POST","/api/auth/login",null,Map.of("username",name,"password",password)); }
    private ResponseEntity<JsonNode> call(String method,String url,String token,Object body) {
        HttpHeaders headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if(token!=null) headers.setBearerAuth(token);
        return http.exchange(url,HttpMethod.valueOf(method),new HttpEntity<>(body,headers),JsonNode.class);
    }
    private JsonNode ok(ResponseEntity<JsonNode> response) { status(response,200); assertThat(response.getBody().path("code").asInt()).isZero(); return response.getBody().path("data"); }
    private void status(ResponseEntity<JsonNode> response,int status) { assertThat(response.getStatusCode().value()).isEqualTo(status); }
    private long customer(String suffix,Long owner,Long team,String sea) {
        jdbc.update("INSERT INTO crm_customer(customer_name,normalized_name,primary_phone,owner_id,team_id,sea_type) VALUES(?,?,'13812345678',?,?,?)",prefix+suffix,prefix+suffix,owner,team,sea);
        long id=jdbc.queryForObject("SELECT id FROM crm_customer WHERE normalized_name=?",Long.class,prefix+suffix);
        customers.add(id); return id;
    }
}
