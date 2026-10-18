package com.bishe.crm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.common.api.PageQuery;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.common.exception.GlobalExceptionHandler;
import com.bishe.crm.config.SecurityConfig;
import com.bishe.crm.module.auth.security.JwtTokenService;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import com.bishe.crm.module.health.controller.HealthController;
import com.bishe.crm.module.health.service.InfrastructureHealthService;
import com.bishe.crm.module.health.vo.HealthVO;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(properties = "crm.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        controllers = {HealthController.class, ApiFoundationTest.FixtureController.class},
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, ApiFoundationTest.FixtureController.class})
class ApiFoundationTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private InfrastructureHealthService healthService;

    @MockitoBean private JwtTokenService tokens;
    @MockitoBean private SystemUserMapper users;

    @Test
    void anonymousHealthReturnsUnifiedSuccessWithoutSecrets() throws Exception {
        when(healthService.check()).thenReturn(health("UP", "UP"));
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data.components.mysql").value("UP"))
                .andExpect(jsonPath("$.data.components.redis").value("UP"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void dependencyFailureReturns503AndFailureCode() throws Exception {
        when(healthService.check()).thenReturn(health("DOWN", "UP"));
        mvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50300))
                .andExpect(jsonPath("$.data.status").value("DOWN"));
    }

    @Test
    void protectedGetAndPostRejectAnonymousWithJson() throws Exception {
        mvc.perform(get("/fixture/page"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(post("/api/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @WithMockUser(roles = "SPECIALIST")
    void methodSecurityDeniesWith403RatherThan500() throws Exception {
        mvc.perform(get("/fixture/admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    @WithMockUser
    void invalidPaginationIsRejected() throws Exception {
        mvc.perform(get("/fixture/page").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(get("/fixture/page").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @WithMockUser
    void businessConflictKeeps409Contract() throws Exception {
        mvc.perform(get("/fixture/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    @WithMockUser
    void databaseConnectionFailureKeeps503ContractWithoutExceptionDetails() throws Exception {
        mvc.perform(get("/fixture/database-unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50300))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @WithMockUser
    void unknownRouteReturnsUnified404() throws Exception {
        mvc.perform(get("/fixture/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    private HealthVO health(String mysql, String redis) {
        return new HealthVO("UP".equals(mysql) && "UP".equals(redis) ? "UP" : "DOWN",
                Map.of("mysql", mysql, "redis", redis), OffsetDateTime.parse("2026-10-17T12:00:00+08:00"));
    }

    @RestController
    static class FixtureController {
        @GetMapping("/fixture/page")
        public PageQuery page(@Valid PageQuery query) { return query; }

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/fixture/admin")
        public String admin() { return "ok"; }

        @GetMapping("/fixture/conflict")
        public void conflict() { throw new BizException(ErrorCode.CONFLICT); }

        @GetMapping("/fixture/database-unavailable")
        public void databaseUnavailable() { throw new DataAccessResourceFailureException("Fixture outage"); }
    }
}
