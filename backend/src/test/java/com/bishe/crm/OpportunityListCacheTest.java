/* 文件作用：验证列表缓存范围隔离、刷新、提交失效、故障降级及查询中提交。 */
package com.bishe.crm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.bishe.crm.common.api.PageResult;
import com.bishe.crm.module.analytics.service.DashboardCache;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.opportunity.dto.OpportunityQuery;
import com.bishe.crm.module.opportunity.service.OpportunityListCache;
import com.bishe.crm.module.opportunity.vo.OpportunitySummaryVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;

class OpportunityListCacheTest {
    final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    final ValueOperations<String,String> values = mock(ValueOperations.class);
    final Map<String,String> stored = new HashMap<>();
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final DashboardCache revisions = new DashboardCache(redis,json);
    final OpportunityListCache cache = new OpportunityListCache(redis,json,revisions);
    final AtomicInteger calls = new AtomicInteger();
    final Supplier<PageResult<OpportunitySummaryVO>> db = () -> {
        calls.incrementAndGet(); var row = new OpportunitySummaryVO(); row.setId(1L);
        return new PageResult<>(1,List.of(row));
    };
    OpportunityListCacheTest() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(i -> stored.get(i.getArgument(0)));
        doAnswer(i -> { assertThat((Duration)i.getArgument(2)).isEqualTo(Duration.ofSeconds(15)); stored.put(i.getArgument(0),i.getArgument(1)); return null; })
            .when(values).set(anyString(),anyString(),any(Duration.class));
    }
    AuthPrincipal actor(long id,String role,Long team,long version) {
        return new AuthPrincipal(id,"test","验证",role,team,"测试团队",false,version);
    }
    @Test void identityFiltersPagingRefreshAndRevisionAreIsolated() {
        var a=actor(1,"SPECIALIST",1L,1); var q=new OpportunityQuery();
        assertThat(cache.list(a,q,db).list().get(0).getId()).isEqualTo(1L);
        cache.list(a,q,db); assertThat(calls.get()).isEqualTo(1);
        cache.list(actor(2,"SPECIALIST",1L,1),q,db);
        cache.list(actor(1,"MANAGER",1L,1),q,db);
        cache.list(actor(1,"SPECIALIST",2L,1),q,db);
        cache.list(actor(1,"SPECIALIST",1L,2),q,db);
        q.setKeyword("报价"); cache.list(a,q,db);
        q.setPage(2); cache.list(a,q,db);
        q.setStageCode("QUOTED"); cache.list(a,q,db);
        q.setActivityStatus("ACTIVE"); cache.list(a,q,db);
        q.setOwnerId(3L); cache.list(a,q,db);
        assertThat(calls.get()).isEqualTo(10);
        q.setRefresh(true); cache.list(a,q,db); assertThat(calls.get()).isEqualTo(11);
        q.setRefresh(false); cache.list(a,q,db); assertThat(calls.get()).isEqualTo(11);
        revisions.invalidate(); cache.list(a,q,db); assertThat(calls.get()).isEqualTo(12);
        assertThat(stored.keySet()).allMatch(k -> k.startsWith("crm:query:opportunities:v1:"));
    }
    @Test void outageAndInvalidJsonUseDatabaseAndConcurrentCommitDoesNotFillOldKey() {
        var a=actor(1,"ADMIN",null,1); var q=new OpportunityQuery();
        cache.list(a,q,db); stored.replaceAll((k,v)->"broken json");
        cache.list(a,q,db); assertThat(calls.get()).isEqualTo(2);
        when(values.get(anyString())).thenThrow(new IllegalStateException("isolated outage"));
        cache.list(a,q,db); assertThat(calls.get()).isEqualTo(3);
        reset(values); when(values.get(anyString())).thenReturn(null);
        cache.list(a,q,()->{ revisions.invalidate(); return db.get(); });
        verify(values,never()).set(anyString(),anyString(),any(Duration.class));
    }
    @Test void unauthenticatedUnknownAndFirstPasswordActorsCannotReadCache() {
        var q=new OpportunityQuery();
        assertThatThrownBy(()->cache.list(null,q,db)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->cache.list(actor(1,"UNKNOWN",1L,1),q,db)).isInstanceOf(RuntimeException.class);
        var first=new AuthPrincipal(1L,"x","x","ADMIN",null,null,true,1);
        assertThatThrownBy(()->cache.list(first,q,db)).isInstanceOf(RuntimeException.class);
        assertThat(calls.get()).isZero(); verifyNoInteractions(values);
    }
}
