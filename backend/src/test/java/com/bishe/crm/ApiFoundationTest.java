/* 文件作用：验证统一响应、异常处理和接口安全基础。 */
package com.bishe.crm;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.common.api.PageQuery;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.common.exception.GlobalExceptionHandler;
import com.bishe.crm.config.SecurityConfig;
import com.bishe.crm.module.auth.security.JwtTokenService;
import com.bishe.crm.module.health.controller.HealthController;
import com.bishe.crm.module.health.service.InfrastructureHealthService;
import com.bishe.crm.module.health.vo.HealthVO;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 验证统一响应、异常处理和接口安全基础。 */
@WebMvcTest(
    properties = "crm.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    controllers = { HealthController.class, ApiFoundationTest.FixtureController.class },
    excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class
)
@Import(
    {
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        ApiFoundationTest.FixtureController.class,
    }
)
class ApiFoundationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private InfrastructureHealthService healthService;

    @MockitoBean
    private JwtTokenService tokens;

    @MockitoBean
    private SystemUserMapper users;

    /** 验证匿名健康检查返回统一成功格式且不泄露配置。 */
    @Test
    void anonymousHealthReturnsUnifiedSuccessWithoutSecrets() throws Exception {
        when(healthService.check()).thenReturn(health("UP", "UP"));
        mvc
            .perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.message").isString())
            .andExpect(jsonPath("$.data.components.mysql").value("UP"))
            .andExpect(jsonPath("$.data.components.redis").value("UP"))
            .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    /** 验证依赖故障返回 HTTP 503 及对应业务码。 */
    @Test
    void dependencyFailureReturns503AndFailureCode() throws Exception {
        when(healthService.check()).thenReturn(health("DOWN", "UP"));
        mvc
            .perform(get("/api/health"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(50300))
            .andExpect(jsonPath("$.data.status").value("DOWN"));
    }

    /** 验证受保护的读写接口拒绝匿名并返回 JSON。 */
    @Test
    void protectedGetAndPostRejectAnonymousWithJson() throws Exception {
        mvc
            .perform(get("/fixture/page"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40100))
            .andExpect(jsonPath("$.data").isEmpty());
        mvc
            .perform(post("/api/customers"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40100));
    }

    /** 验证方法权限拒绝返回 403。 */
    @Test
    @WithMockUser(roles = "SPECIALIST")
    void methodSecurityDeniesWith403RatherThan500() throws Exception {
        mvc
            .perform(get("/fixture/admin"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40300));
    }

    /** 验证非法分页参数被拒绝。 */
    @Test
    @WithMockUser
    void invalidPaginationIsRejected() throws Exception {
        mvc
            .perform(get("/fixture/page").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40001));
        mvc
            .perform(get("/fixture/page").param("page", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40001));
    }

    /** 验证业务冲突保留 HTTP 409 契约。 */
    @Test
    @WithMockUser
    void businessConflictKeeps409Contract() throws Exception {
        mvc
            .perform(get("/fixture/conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(40900));
    }

    /** 验证数据库连接失败返回 503 且隐藏异常细节。 */
    @Test
    @WithMockUser
    void databaseConnectionFailureKeeps503ContractWithoutExceptionDetails() throws Exception {
        mvc
            .perform(get("/fixture/database-unavailable"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(50300))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    /** 验证未知接口返回统一 404。 */
    @Test
    @WithMockUser
    void unknownRouteReturnsUnified404() throws Exception {
        mvc
            .perform(get("/fixture/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(40400));
    }

    /** 提供健康检查测试响应。 */
    private HealthVO health(String mysql, String redis) {
        return new HealthVO(
            "UP".equals(mysql) && "UP".equals(redis) ? "UP" : "DOWN",
            Map.of("mysql", mysql, "redis", redis),
            OffsetDateTime.parse("2026-10-17T12:00:00+08:00")
        );
    }

    /** 仅用于测试统一响应和权限的模拟接口。 */
    @RestController
    static class FixtureController {

        /** 提供分页校验测试入口。 */
        @GetMapping("/fixture/page")
        public PageQuery page(@Valid PageQuery query) {
            return query;
        }

        /** 提供仅管理员可访问的测试入口。 */
        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/fixture/admin")
        public String admin() {
            return "ok";
        }

        /** 触发预期的业务冲突供断言。 */
        @GetMapping("/fixture/conflict")
        public void conflict() {
            throw new BizException(ErrorCode.CONFLICT);
        }

        /** 触发数据库不可用异常供断言。 */
        @GetMapping("/fixture/database-unavailable")
        public void databaseUnavailable() {
            throw new DataAccessResourceFailureException("Fixture outage");
        }
    }
}
