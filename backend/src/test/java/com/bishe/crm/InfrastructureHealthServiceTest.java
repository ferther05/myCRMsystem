package com.bishe.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bishe.crm.module.health.mapper.DatabaseProbeMapper;
import com.bishe.crm.module.health.service.InfrastructureHealthService;
import com.bishe.crm.module.health.vo.HealthVO;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class InfrastructureHealthServiceTest {
    @Mock private DatabaseProbeMapper databaseProbeMapper;
    @Mock private StringRedisTemplate redisTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void databaseFailureDoesNotHideRedisResultOrExposeConnectionDetails() {
        when(databaseProbeMapper.ping()).thenThrow(new IllegalStateException("private-connection-details"));
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(true);
        Clock clock = Clock.fixed(Instant.parse("2026-10-17T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        HealthVO result = new InfrastructureHealthService(databaseProbeMapper, redisTemplate, clock).check();
        assertThat(result.status()).isEqualTo("DOWN");
        assertThat(result.components()).containsEntry("mysql", "DOWN").containsEntry("redis", "UP");
        assertThat(result.checkedAt().toString()).isEqualTo("2026-10-17T12:00+08:00");
        assertThat(result.toString()).doesNotContain("private-connection-details");
    }
}
