/* 文件作用：验证真实 MySQL、Redis 及 MyBatis 基础设施。 */
package com.bishe.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bishe.crm.common.api.PageResult;
import com.bishe.crm.module.system.entity.SystemConfig;
import com.bishe.crm.module.system.mapper.SystemConfigMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 验证真实 MySQL、Redis 及 MyBatis 基础设施。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class BackendInfrastructureIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SystemConfigMapper configMapper;

    @Autowired
    private MockMvc mvc;

    /** 验证应用专用账号及种子数据的真实数据库连接。 */
    @Test
    void connectsToCrmUsingScopedAppAccountAndExistingSeeds() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("crm_system");
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo(
            "+08:00"
        );
        assertThat(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)).startsWith(
            "crm_app@"
        );
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema=DATABASE() AND table_type='BASE TABLE'",
                Long.class
            )
        ).isEqualTo(20);
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE role_code='ADMIN' " +
                    "AND account_status='ACTIVE'",
                Long.class
            )
        ).isGreaterThanOrEqualTo(1);
        assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM sys_team WHERE enabled=1", Long.class)
        ).isGreaterThanOrEqualTo(2);
        String grants = String.join(
            "\n",
            jdbc.queryForList("SHOW GRANTS FOR CURRENT_USER()", String.class)
        );
        assertThat(grants)
            .contains("SELECT, INSERT, UPDATE, DELETE", "`crm_system`.*")
            .doesNotContain("ALL PRIVILEGES", "WITH GRANT OPTION", "CREATE", "DROP", "ALTER");
        SystemConfig timezone = configMapper.selectOne(
            new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigType, "PARAM")
                .eq(SystemConfig::getConfigGroup, "crm.system")
                .eq(SystemConfig::getConfigKey, "business_timezone")
        );
        assertThat(timezone.getConfigValue()).isEqualTo("Asia/Shanghai");
    }

    /** 验证真实 MySQL 分页、自动填充和乐观锁。 */
    @Test
    @Transactional
    void realMySqlPaginationAutoFillAndOptimisticLockWork() {
        String group = "it." + UUID.randomUUID();
        SystemConfig config = new SystemConfig();
        config.setConfigType("PARAM");
        config.setConfigGroup(group);
        config.setConfigKey("optimistic-lock-check");
        config.setConfigValue("original");
        config.setDisplayName("Temporary integration fixture");
        config.setSortNo(0);
        config.setEnabled(true);
        assertThat(configMapper.insert(config)).isEqualTo(1);
        assertThat(config.getId()).isPositive();
        assertThat(config.getVersion()).isZero();
        assertThat(config.getCreatedAt()).isNotNull();
        assertThat(config.getUpdatedAt()).isNotNull();

        Page<SystemConfig> page = configMapper.selectPage(
            new Page<>(1, 1),
            new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigGroup, group)
                .orderByAsc(SystemConfig::getId)
        );
        assertThat(PageResult.from(page).total()).isEqualTo(1);
        assertThat(PageResult.from(page).list()).hasSize(1);
        Page<SystemConfig> nextPage = configMapper.selectPage(
            new Page<>(2, 1),
            new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigGroup, group)
                .orderByAsc(SystemConfig::getId)
        );
        assertThat(nextPage.getTotal()).isEqualTo(1);
        assertThat(nextPage.getRecords()).isEmpty();

        SystemConfig first = configMapper.selectById(config.getId());
        SystemConfig stale = configMapper.selectById(config.getId());
        first.setConfigValue("accepted");
        assertThat(configMapper.updateById(first)).isEqualTo(1);
        stale.setConfigValue("must-not-overwrite");
        assertThat(configMapper.updateById(stale)).isZero();
        SystemConfig saved = configMapper.selectById(config.getId());
        assertThat(saved.getConfigValue()).isEqualTo("accepted");
        assertThat(saved.getVersion()).isEqualTo(1);
        // 测试事务会回滚此临时数据，不修改种子记录。
    }

    /** 验证真实健康检查及匿名业务请求拦截。 */
    @Test
    void realHttpFilterChainReportsBothDependenciesUpAndRejectsAnonymousBusinessAccess()
        throws Exception {
        mvc
            .perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.components.mysql").value("UP"))
            .andExpect(jsonPath("$.data.components.redis").value("UP"));
        mvc
            .perform(get("/api/customers"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40100));
    }
}
