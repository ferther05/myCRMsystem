/* 文件作用：商机列表短时查询缓存，按身份、分页和筛选隔离，沿用提交后失效版本。 */
package com.bishe.crm.module.opportunity.service;

import com.bishe.crm.common.api.*;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.analytics.service.DashboardCache;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.opportunity.dto.OpportunityQuery;
import com.bishe.crm.module.opportunity.vo.OpportunitySummaryVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpportunityListCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final DashboardCache revisions;
    private static final TypeReference<PageResult<OpportunitySummaryVO>> TYPE = new TypeReference<>() {};

    /** 认证和参数校验后才可命中缓存；未命中或Redis故障均执行原有授权数据库查询。 */
    public PageResult<OpportunitySummaryVO> list(AuthPrincipal actor, OpportunityQuery query,
            Supplier<PageResult<OpportunitySummaryVO>> database) {
        if (actor == null) throw new BizException(ErrorCode.UNAUTHORIZED);
        if (actor.mustChangePassword()) throw new BizException(ErrorCode.PASSWORD_CHANGE_REQUIRED);
        if (!Set.of("ADMIN", "MANAGER", "SPECIALIST").contains(actor.roleCode())) throw new BizException(ErrorCode.FORBIDDEN);
        String identity;
        try {
            // 摘要隐藏搜索内容；完整身份包含当前角色、团队及账号版本。
            byte[] bytes = json.writeValueAsBytes(Arrays.asList(actor, query.getPage(), query.getSize(), query.getKeyword(), query.getStageCode(), query.getActivityStatus(), query.getOwnerId()));
            identity = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) { return database.get(); }
        String key = key(identity);
        if (!query.isRefresh()) {
            try {
                String cached = redis.opsForValue().get(key);
                if (cached != null && key.equals(key(identity))) return json.readValue(cached, TYPE);
            } catch (Exception ignored) { /* 原数据库查询保留权限与异常语义。 */ }
        }
        PageResult<OpportunitySummaryVO> result = database.get();
        // 查询期间发生提交则不回填旧结果。旧代键15秒自然过期。
        if (key.equals(key(identity))) {
            try { redis.opsForValue().set(key, json.writeValueAsString(result), Duration.ofSeconds(15)); }
            catch (Exception ignored) { /* 缓存写失败不影响查询。 */ }
        }
        return result;
    }

    /** 与看板共享实例及提交版本，不缓存业务明细和写入接口。 */
    private String key(String identity) {
        return revisions.key(identity).replace("crm:dashboard:v1:", "crm:query:opportunities:v1:");
    }
}
