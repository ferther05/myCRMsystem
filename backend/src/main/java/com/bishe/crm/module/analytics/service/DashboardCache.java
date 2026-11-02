/* 文件作用：单实例看板缓存，角色范围隔离，事务提交后换代失效，Redis故障回源。 */
package com.bishe.crm.module.analytics.service;

import com.bishe.crm.module.analytics.vo.DashboardViews;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class DashboardCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final String instance=UUID.randomUUID().toString();
    private final AtomicLong revision=new AtomicLong();
    /** 新提交使全部旧聚合缓存不可再命中，旧键60秒内自然清理。 */
    public void invalidate() {revision.incrementAndGet();}
    /** 键包含实例、提交版本及已授权查询身份，避免重启或角色切换复用旧结果。 */
    public String key(String identity) {return "crm:dashboard:v1:"+instance+":"+revision.get()+":"+identity;}
    /** 缓存数据不可用或格式变化时返回数据库查询路径。 */
    public DashboardViews.Result get(String key) {
        try {String value=redis.opsForValue().get(key);return value==null?null:json.readValue(value,DashboardViews.Result.class);} catch(Exception e) {return null;}
    }
    /** 缓存写失败不影响已完成的只读统计。 */
    public void put(String key,DashboardViews.Result result) {
        try {redis.opsForValue().set(key,json.writeValueAsString(result),Duration.ofSeconds(60));}catch(Exception ignored) { }
    }
}
