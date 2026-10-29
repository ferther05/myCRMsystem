/* 文件作用：在真实接口与数据库中验证登录、改密和会话失效。 */
package com.bishe.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.bishe.crm.module.auth.security.JwtProperties;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 在真实接口与数据库中验证登录、改密和会话失效。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(AuthFlowIT.AuthFixtureController.class)
class AuthFlowIT {

    private static final String OLD_PASSWORD = "FixtureOld123!";
    private static final String NEW_PASSWORD = "FixtureNew456!";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private JwtProperties jwt;

    @MockitoSpyBean
    private SystemUserMapper users;

    private final List<Long> fixtureUsers = new ArrayList<>();
    private Long teamId;
    private Fixture specialist;
    private String fixtureHash;

    /** 准备隔离的测试账号及认证环境。 */
    @BeforeEach
    void setUp() {
        String teamCode = "IT-" + UUID.randomUUID().toString().substring(0, 20);
        jdbc.update(
            "INSERT INTO sys_team(team_code,team_name,enabled) VALUES (?,?,1)",
            teamCode,
            teamCode
        );
        teamId = jdbc.queryForObject(
            "SELECT id FROM sys_team WHERE team_code=?",
            Long.class,
            teamCode
        );
        fixtureHash = passwords.encode(OLD_PASSWORD);
        specialist = createUser("SPECIALIST", true, teamId);
    }

    /** 清理本测试创建的隔离数据。 */
    @AfterEach
    void cleanUp() {
        // 只清理本测试明确记录的唯一主键，不删除既有账号。
        for (Long id : fixtureUsers) {
            jdbc.update("DELETE FROM crm_operation_log WHERE operator_id=?", id);
            jdbc.update("DELETE FROM sys_user WHERE id=?", id);
        }
        if (teamId != null) {
            jdbc.update("DELETE FROM sys_team WHERE id=?", teamId);
        }
    }

    /** 只读验证未使用种子账号的初始密码哈希。 */
    @Test
    void unusedInitialSeedPasswordHashesMatchLocalCredentialsWithoutChangingAccounts()
        throws Exception {
        var seeds = jdbc.queryForList(
            "SELECT username,password_hash FROM sys_user " +
                "WHERE username IN ('admin','east.manager','east.sales','south.manager','south.sales') " +
                "AND version=0 AND last_login_at IS NULL AND must_change_password=1"
        );
        // 真实用户登录或改密后不再适用初始凭据校验。
        if (seeds.isEmpty()) {
            return;
        }
        String localCredentials = Files.readString(Path.of("../database/local-credentials.md"));
        for (var seed : seeds) {
            String username = (String) seed.get("username");
            String hash = (String) seed.get("password_hash");
            boolean matched = localCredentials
                .lines()
                .filter(line -> line.contains(username))
                .flatMap(line -> java.util.Arrays.stream(line.replace("`", "").split("\\|")))
                .map(String::trim)
                .filter(value -> !value.isBlank() && value.length() <= 72)
                .anyMatch(value -> passwords.matches(value, hash));
            assertThat(matched).as("local credential hash match for %s", username).isTrue();
        }
    }

    /** 验证三类角色登录及实际方法权限。 */
    @Test
    void threeRolesLoginAndMethodSecurityUsesTheirActualRole() {
        Fixture admin = createUser("ADMIN", false, null);
        Fixture manager = createUser("MANAGER", false, teamId);
        for (Fixture user : List.of(admin, manager)) {
            String token = login(user, OLD_PASSWORD);
            var me = request(HttpMethod.GET, "/api/auth/me", token, null);
            assertResponse(me, 200, 0);
            assertThat(me.getBody().path("data").path("roleCode").asText()).isEqualTo(user.role());
            var protectedResponse = request(HttpMethod.GET, "/auth-fixture/admin", token, null);
            assertResponse(
                protectedResponse,
                user.role().equals("ADMIN") ? 200 : 403,
                user.role().equals("ADMIN") ? 0 : 40300
            );
        }
        String token = login(specialist, OLD_PASSWORD);
        assertResponse(request(HttpMethod.GET, "/api/auth/me", token, null), 200, 0);
    }

    /** 验证首次改密限制、事务审计及重新登录要求。 */
    @Test
    void firstPasswordChangeIsEnforcedAndCommittedWithAuditThenRequiresLoginAgain() {
        String token = login(specialist, OLD_PASSWORD);
        var me = request(HttpMethod.GET, "/api/auth/me?userId=1", token, null);
        assertThat(me.getBody().path("data").path("id").asLong()).isEqualTo(specialist.id());
        assertThat(me.getBody().path("data").path("mustChangePassword").asBoolean()).isTrue();
        assertResponse(request(HttpMethod.GET, "/api/customers", token, null), 403, 40301);
        assertResponse(request(HttpMethod.POST, "/auth-fixture/admin", token, null), 403, 40301);

        assertResponse(change(token, "incorrect", NEW_PASSWORD, NEW_PASSWORD), 400, 40002);
        assertResponse(change(token, OLD_PASSWORD, OLD_PASSWORD, OLD_PASSWORD), 400, 40003);
        assertResponse(change(token, OLD_PASSWORD, "short1", "short1"), 400, 40001);
        assertResponse(change(token, OLD_PASSWORD, "abcdefgh", "abcdefgh"), 400, 40001);
        assertResponse(change(token, OLD_PASSWORD, NEW_PASSWORD, "Mismatch123!"), 400, 40001);
        assertThat(auditCount(specialist.id())).isZero();
        assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM sys_user WHERE id=?",
                String.class,
                specialist.id()
            )
        ).isEqualTo(fixtureHash);

        assertResponse(change(token, OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD), 200, 0);
        var saved = jdbc.queryForMap(
            "SELECT password_hash,must_change_password,version FROM sys_user WHERE id=?",
            specialist.id()
        );
        assertThat(passwords.matches(NEW_PASSWORD, (String) saved.get("password_hash"))).isTrue();
        assertThat(saved.get("password_hash")).isNotEqualTo(NEW_PASSWORD).isNotEqualTo(fixtureHash);
        assertThat(((Number) saved.get("version")).longValue()).isEqualTo(2);
        assertThat(
            jdbc.queryForObject(
                "SELECT must_change_password=0 FROM sys_user WHERE id=?",
                Boolean.class,
                specialist.id()
            )
        ).isTrue();
        assertThat(auditCount(specialist.id())).isEqualTo(1);
        String reason = jdbc.queryForObject(
            "SELECT reason FROM crm_operation_log WHERE operator_id=?",
            String.class,
            specialist.id()
        );
        assertThat(reason).doesNotContain(OLD_PASSWORD, NEW_PASSWORD, fixtureHash, token);
        assertResponse(request(HttpMethod.GET, "/api/auth/me", token, null), 401, 40102);
        assertResponse(loginResponse(specialist.username(), OLD_PASSWORD), 401, 40101);
        String newToken = login(specialist, NEW_PASSWORD);
        var newMe = request(HttpMethod.GET, "/api/auth/me", newToken, null);
        assertThat(newMe.getBody().path("data").path("mustChangePassword").asBoolean()).isFalse();
        assertResponse(request(HttpMethod.GET, "/auth-fixture/read", newToken, null), 200, 0);
        assertResponse(request(HttpMethod.GET, "/auth-fixture/admin", newToken, null), 403, 40300);
    }

    /** 验证错误密码与不存在账号的响应一致且输入受校验。 */
    @Test
    void wrongAndMissingAccountsAreIndistinguishableAndInputsAreValidated() {
        var wrong = loginResponse(specialist.username(), "WrongPassword123!");
        var missing = loginResponse("no-such-" + UUID.randomUUID(), OLD_PASSWORD);
        assertResponse(wrong, 401, 40101);
        assertResponse(missing, 401, 40101);
        assertThat(wrong.getBody()).isEqualTo(missing.getBody());
        assertResponse(loginResponse("", OLD_PASSWORD), 400, 40001);
        assertResponse(loginResponse(specialist.username(), "x".repeat(73)), 400, 40001);
        assertResponse(
            request(HttpMethod.POST, "/api/auth/login", null, Map.of("username", "somebody")),
            400,
            40001
        );
        assertResponse(request(HttpMethod.GET, "/api/auth/me", null, null), 401, 40100);
        assertResponse(
            request(
                HttpMethod.POST,
                "/api/auth/password",
                null,
                Map.of("oldPassword", OLD_PASSWORD, "newPassword", NEW_PASSWORD)
            ),
            401,
            40100
        );
    }

    /** 验证新密码长度边界和额外身份字段无法绕过服务端。 */
    @Test
    void newPasswordLengthBoundaryAndExtraIdentityFieldsCannotBypassTheServer() {
        var response = request(
            HttpMethod.POST,
            "/api/auth/login",
            null,
            Map.of(
                "username",
                specialist.username(),
                "password",
                OLD_PASSWORD,
                "roleCode",
                "ADMIN",
                "mustChangePassword",
                false,
                "id",
                1
            )
        );
        assertResponse(response, 200, 0);
        assertThat(
            response.getBody().path("data").path("user").path("roleCode").asText()
        ).isEqualTo("SPECIALIST");
        assertThat(
            response.getBody().path("data").path("user").path("mustChangePassword").asBoolean()
        ).isTrue();
        String token = response.getBody().path("data").path("accessToken").asText();
        String maxLength = "A" + "1".repeat(63);
        assertResponse(change(token, OLD_PASSWORD, maxLength + "1", maxLength + "1"), 400, 40001);
        assertResponse(
            change(token, OLD_PASSWORD, "密码Password123", "密码Password123"),
            400,
            40001
        );
        assertResponse(change(token, OLD_PASSWORD, maxLength, maxLength), 200, 0);
        login(specialist, maxLength);
    }

    /** 验证 HTTP 层拒绝格式错误、过期、无签名及错签令牌。 */
    @Test
    void malformedExpiredUnsignedAndWronglySignedTokensAreRejectedOverHttp() {
        String token = login(specialist, OLD_PASSWORD);
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwt.getSecret()));
        String expired = Jwts.builder()
            .issuer(jwt.getIssuer())
            .subject(specialist.id().toString())
            .id("expired-fixture")
            .issuedAt(Date.from(Instant.now().minusSeconds(100)))
            .expiration(Date.from(Instant.now().minusSeconds(1)))
            .claim("ver", 1L)
            .signWith(key, Jwts.SIG.HS256)
            .compact();
        var wrongKey = Jwts.SIG.HS256.key().build();
        String forged = Jwts.builder()
            .issuer(jwt.getIssuer())
            .subject(specialist.id().toString())
            .expiration(Date.from(Instant.now().plusSeconds(60)))
            .claim("role", "ADMIN")
            .signWith(wrongKey)
            .compact();
        for (String invalid : List.of(
            "bad-token",
            expired,
            forged,
            Jwts.builder().subject(specialist.id().toString()).compact()
        )) {
            var response = request(HttpMethod.GET, "/api/auth/me", invalid, null);
            assertResponse(response, 401, 40102);
            assertThat(response.getBody().toString()).doesNotContain(
                "Exception",
                "password",
                token
            );
        }
        assertResponse(request(HttpMethod.GET, "/api/auth/me", token, null), 200, 0);
        // 上一请求的认证身份不得残留到下一匿名请求。
        assertResponse(request(HttpMethod.GET, "/api/auth/me", null, null), 401, 40100);
    }

    /** 验证再次登录及退出撤销旧会话。 */
    @Test
    void anotherLoginAndLogoutRevokePreviousTokensIncludingFirstLoginSession() {
        String oldToken = login(specialist, OLD_PASSWORD);
        String current = login(specialist, OLD_PASSWORD);
        assertResponse(request(HttpMethod.POST, "/api/auth/logout", oldToken, null), 401, 40102);
        assertResponse(request(HttpMethod.GET, "/api/auth/me", current, null), 200, 0);
        assertResponse(request(HttpMethod.POST, "/api/auth/logout", current, null), 200, 0);
        assertResponse(request(HttpMethod.GET, "/api/auth/me", current, null), 401, 40102);
        assertThat(auditCount(specialist.id())).isZero();
    }

    /** 验证停用、待审及团队停用账号不可登录或使用旧令牌。 */
    @Test
    void disabledPendingAndDisabledTeamAccountsCannotUseOldTokensOrLogin() {
        String token = login(specialist, OLD_PASSWORD);
        for (String status : List.of("DISABLED", "PENDING")) {
            jdbc.update("UPDATE sys_user SET account_status=? WHERE id=?", status, specialist.id());
            assertResponse(request(HttpMethod.GET, "/api/auth/me", token, null), 401, 40102);
            assertResponse(loginResponse(specialist.username(), OLD_PASSWORD), 401, 40101);
        }
        jdbc.update("UPDATE sys_user SET account_status='ACTIVE' WHERE id=?", specialist.id());
        jdbc.update("UPDATE sys_team SET enabled=0 WHERE id=?", teamId);
        assertResponse(request(HttpMethod.GET, "/api/auth/me", token, null), 401, 40102);
        assertResponse(loginResponse(specialist.username(), OLD_PASSWORD), 401, 40101);
    }

    /** 验证角色、团队和首次改密标记读取数据库最新值。 */
    @Test
    void roleTeamAndFirstPasswordFlagAreReadFromDatabaseRatherThanStaleClaims() {
        Fixture admin = createUser("ADMIN", false, null);
        String token = login(admin, OLD_PASSWORD);
        assertResponse(request(HttpMethod.GET, "/auth-fixture/admin", token, null), 200, 0);
        jdbc.update(
            "UPDATE sys_user SET role_code='SPECIALIST',team_id=? WHERE id=?",
            teamId,
            admin.id()
        );
        assertResponse(request(HttpMethod.GET, "/auth-fixture/admin", token, null), 403, 40300);
        var me = request(HttpMethod.GET, "/api/auth/me", token, null);
        assertThat(me.getBody().path("data").path("teamId").asLong()).isEqualTo(teamId);
        assertThat(me.getBody().path("data").path("roleCode").asText()).isEqualTo("SPECIALIST");
        jdbc.update("UPDATE sys_user SET must_change_password=1 WHERE id=?", admin.id());
        assertResponse(request(HttpMethod.GET, "/auth-fixture/read", token, null), 403, 40301);
    }

    /** 验证并发改密只提交一份密码及审计。 */
    @Test
    void concurrentPasswordChangesCommitExactlyOneHashAndOneAudit() throws Exception {
        String token = login(specialist, OLD_PASSWORD);
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("start timeout");
                }
                return change(token, OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
            });
            String alternative = "AnotherNew789!";
            var second = pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("start timeout");
                }
                return change(token, OLD_PASSWORD, alternative, alternative);
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);
            assertThat(
                List.of(a, b)
                    .stream()
                    .filter(response -> response.getStatusCode().value() == 200)
                    .count()
            ).isEqualTo(1);
            assertThat(
                List.of(a, b)
                    .stream()
                    .filter(response -> response.getStatusCode().value() != 200)
                    .map(response -> response.getStatusCode().value())
                    .toList()
            ).allMatch(code -> code == 401 || code == 409);
            assertThat(auditCount(specialist.id())).isEqualTo(1);
            String winner = a.getStatusCode().value() == 200 ? NEW_PASSWORD : alternative;
            assertThat(
                passwords.matches(
                    winner,
                    jdbc.queryForObject(
                        "SELECT password_hash FROM sys_user WHERE id=?",
                        String.class,
                        specialist.id()
                    )
                )
            ).isTrue();
            assertResponse(request(HttpMethod.GET, "/api/auth/me", token, null), 401, 40102);
            login(specialist, winner);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** 验证审计失败回滚密码、首次改密标记和版本。 */
    @Test
    void auditFailureRollsBackPasswordFlagAndVersionInRealDatabase() {
        String token = login(specialist, OLD_PASSWORD);
        doThrow(new DataIntegrityViolationException("Injected audit write failure"))
            .when(users)
            .insertPasswordAudit(eq(specialist.id()), eq(teamId), any());
        assertResponse(change(token, OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD), 409, 40900);
        assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM sys_user WHERE id=?",
                String.class,
                specialist.id()
            )
        ).isEqualTo(fixtureHash);
        assertThat(
            jdbc.queryForObject(
                "SELECT must_change_password=1 AND version=1 FROM sys_user WHERE id=?",
                Boolean.class,
                specialist.id()
            )
        ).isTrue();
        assertThat(auditCount(specialist.id())).isZero();
        assertResponse(request(HttpMethod.GET, "/api/auth/me", token, null), 200, 0);
    }

    /** 创建测试用账号。 */
    private Fixture createUser(String role, boolean mustChange, Long team) {
        String username = "auth.it." + UUID.randomUUID();
        jdbc.update(
            "INSERT INTO sys_user(username,password_hash,real_name,role_code,team_id," +
                "account_status,must_change_password) VALUES (?,?,?,?,?,'ACTIVE',?)",
            username,
            fixtureHash,
            "Temporary auth fixture",
            role,
            team,
            mustChange
        );
        Long id = jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE username=?",
            Long.class,
            username
        );
        fixtureUsers.add(id);
        return new Fixture(id, username, role);
    }

    /** 查询测试目标的审计数量。 */
    private int auditCount(Long id) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_operation_log WHERE biz_type='USER' " +
                "AND biz_id=? AND action_code='PASSWORD_CHANGED'",
            Integer.class,
            id
        );
    }

    /** 提交账号登录并返回令牌。 */
    private String login(Fixture user, String password) {
        var response = loginResponse(user.username(), password);
        assertResponse(response, 200, 0);
        JsonNode data = response.getBody().path("data");
        assertThat(data.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(data.path("expiresIn").asLong()).isEqualTo(7200);
        assertThat(data.path("accessToken").asText()).isNotBlank();
        assertThat(data.path("user").has("passwordHash")).isFalse();
        assertThat(data.toString()).doesNotContain(OLD_PASSWORD, NEW_PASSWORD, fixtureHash);
        return data.path("accessToken").asText();
    }

    /** 发送测试登录请求并保留原始响应。 */
    private ResponseEntity<JsonNode> loginResponse(String username, String password) {
        return request(
            HttpMethod.POST,
            "/api/auth/login",
            null,
            Map.of("username", username, "password", password)
        );
    }

    /** 发送测试修改密码请求。 */
    private ResponseEntity<JsonNode> change(String token, String old, String next, String confirm) {
        return request(
            HttpMethod.POST,
            "/api/auth/password",
            token,
            Map.of("oldPassword", old, "newPassword", next, "confirmPassword", confirm)
        );
    }

    /** 构造带测试身份的请求。 */
    private ResponseEntity<JsonNode> request(
        HttpMethod method,
        String path,
        String token,
        Object body
    ) {
        var headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    /** 断言响应状态与业务码。 */
    private void assertResponse(ResponseEntity<JsonNode> response, int status, int code) {
        // 断言失败时不输出含登录令牌的完整响应。
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("code").asInt()).isEqualTo(code);
        assertThat(response.getHeaders().getFirst("Set-Cookie")).isNull();
    }

    /** 认证测试账号及其初始密码信息。 */
    private record Fixture(Long id, String username, String role) {}

    /** 仅用于测试认证权限的模拟接口。 */
    @RestController
    static class AuthFixtureController {

        /** 提供仅管理员可访问的测试入口。 */
        @GetMapping("/auth-fixture/admin")
        @PreAuthorize("hasRole('ADMIN')")
        public Map<String, Object> admin() {
            return Map.of("code", 0);
        }

        /** 读取测试接口的响应数据。 */
        @GetMapping("/auth-fixture/read")
        public Map<String, Object> read() {
            return Map.of("code", 0);
        }
    }
}
