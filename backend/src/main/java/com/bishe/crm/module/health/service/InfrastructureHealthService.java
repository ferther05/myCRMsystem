/* 文件作用：独立探测 MySQL 与 Redis 并汇总健康状态。 */
package com.bishe.crm.module.health.service;

import com.bishe.crm.module.health.mapper.DatabaseProbeMapper;
import com.bishe.crm.module.health.vo.HealthVO;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 独立探测 MySQL 与 Redis 并汇总健康状态。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfrastructureHealthService {

    private final DatabaseProbeMapper databaseProbeMapper;
    private final StringRedisTemplate redisTemplate;
    private final Clock businessClock;

    /** 分别探测 MySQL 与 Redis 并汇总健康结果。 */
    public HealthVO check() {
        Map<String, String> components = new LinkedHashMap<>();
        components.put(
            "mysql",
            probe("mysql", () -> Integer.valueOf(1).equals(databaseProbeMapper.ping()))
        );
        components.put(
            "redis",
            probe("redis", () ->
                Boolean.TRUE.equals(
                    redisTemplate.execute(
                        (org.springframework.data.redis.core.RedisCallback<Boolean>) connection ->
                            "PONG".equals(connection.ping())
                    )
                )
            )
        );
        String status = components.values().stream().allMatch("UP"::equals) ? "UP" : "DOWN";
        return new HealthVO(
            status,
            components,
            OffsetDateTime.now(businessClock).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        );
    }

    /** 执行单项依赖检查，失败时只返回安全状态。 */
    private String probe(String component, BooleanSupplier check) {
        try {
            return check.getAsBoolean() ? "UP" : "DOWN";
        } catch (Exception exception) {
            log.warn(
                "Infrastructure probe failed: component={}, type={}",
                component,
                exception.getClass().getSimpleName()
            );
            return "DOWN";
        }
    }
}
