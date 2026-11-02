/* 文件作用：验证Redis故障回源和失效代次，不依赖外部服务来模拟故障。 */
package com.bishe.crm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.bishe.crm.module.analytics.service.DashboardCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class DashboardCacheTest {
    /** Redis连接失败不阻止只读查询，业务写提交失效不依赖Redis成功。 */
    @Test void redisFailureFallsBackAndRevisionStillAdvances() {
        var redis=mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("isolated Redis outage"));
        var cache=new DashboardCache(redis,new ObjectMapper());String before=cache.key("user:team:dates");
        assertThat(cache.get(before)).isNull();assertThatCode(()->cache.put(before,null)).doesNotThrowAnyException();
        cache.invalidate();assertThat(cache.key("user:team:dates")).isNotEqualTo(before);
    }
}
