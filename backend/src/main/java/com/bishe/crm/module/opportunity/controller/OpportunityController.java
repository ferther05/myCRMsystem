/* 文件作用：商机档案、阶段、协作、跟进和统计接口。 */
package com.bishe.crm.module.opportunity.controller;

import com.bishe.crm.common.api.ApiResponse;
import com.bishe.crm.common.api.PageResult;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.customer.dto.CustomerRequests;
import com.bishe.crm.module.customer.service.CustomerService;
import com.bishe.crm.module.opportunity.dto.OpportunityQuery;
import com.bishe.crm.module.opportunity.dto.OpportunityRequests;
import com.bishe.crm.module.opportunity.service.OpportunityActivityService;
import com.bishe.crm.module.opportunity.service.OpportunityService;
import com.bishe.crm.module.opportunity.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 商机档案、阶段、协作、跟进和统计接口。 */
@RestController
@RequestMapping("/api/opportunities")
@RequiredArgsConstructor
@Validated
public class OpportunityController {

    private final OpportunityService opportunities;
    private final OpportunityActivityService activity;
    private final CustomerService customers;

    /** 查询可作为负责人的启用账号。 */
    @GetMapping("/owners")
    public ApiResponse<java.util.List<java.util.Map<String, Object>>> owners(
        @AuthenticationPrincipal AuthPrincipal actor
    ) {
        return ApiResponse.success(opportunities.owners(actor));
    }

    /** 管理员手动执行商机活跃度扫描。 */
    @PostMapping("/activity-scan")
    public ApiResponse<java.util.Map<String, Object>> scan(
        @AuthenticationPrincipal AuthPrincipal actor
    ) {
        if (
            !"ADMIN".equals(actor.roleCode())
        ) throw new com.bishe.crm.common.exception.BizException(
            com.bishe.crm.common.api.ErrorCode.FORBIDDEN
        );
        return ApiResponse.success(activity.scanNow(actor.id()));
    }

    /** 登记报价或客户合作确认的业务证据。 */
    @PostMapping("/{id}/evidence")
    public ApiResponse<Void> evidence(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody OpportunityRequests.Evidence request
    ) {
        opportunities.evidence(actor, id, request);
        return ApiResponse.success(null);
    }

    /** 提交待关闭商机的裁决。 */
    @PostMapping("/{id}/close-decision")
    public ApiResponse<Void> decide(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody OpportunityRequests.PendingDecision request
    ) {
        opportunities.decidePending(actor, id, request);
        return ApiResponse.success(null);
    }

    /** 查询当前商机可选的团队协作成员。 */
    @GetMapping("/{id}/members")
    public ApiResponse<java.util.List<java.util.Map<String, Object>>> members(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id
    ) {
        return ApiResponse.success(opportunities.members(actor, id));
    }

    /** 新增当前商机的跟进记录。 */
    @PostMapping("/{id}/follow-records")
    public ApiResponse<Long> follow(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody CustomerRequests.FollowWrite request
    ) {
        var row = opportunities.detail(actor, id);
        var bound = new CustomerRequests.FollowWrite(
            request.customerVersion(),
            id,
            request.contactId(),
            request.followMethod(),
            request.content(),
            request.resultCode(),
            request.substantiveResult(),
            request.nextAction(),
            request.nextActionAt(),
            request.followedAt()
        );
        return ApiResponse.success(customers.addFollow(actor, row.getCustomerId(), bound));
    }

    /** 按当前用户可见范围分页查询记录。 */
    @GetMapping
    public ApiResponse<PageResult<OpportunitySummaryVO>> list(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Valid OpportunityQuery query
    ) {
        return ApiResponse.success(opportunities.list(actor, query));
    }

    /** 查询详情并检查当前用户的数据权限。 */
    @GetMapping("/{id}")
    public ApiResponse<OpportunityDetailVO> detail(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id
    ) {
        return ApiResponse.success(opportunities.detail(actor, id));
    }

    /** 仅管理员创建并分配商机。 */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Long> create(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Valid @RequestBody OpportunityRequests.Create request
    ) {
        return ApiResponse.success(opportunities.create(actor, request));
    }

    /** 更新商机基本信息，不直接推进阶段。 */
    @PutMapping("/{id}")
    public ApiResponse<Void> edit(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody OpportunityRequests.Edit request
    ) {
        opportunities.edit(actor, id, request);
        return ApiResponse.success(null);
    }

    /** 由管理员按门槛修正相邻阶段。 */
    @PatchMapping("/{id}/stage")
    public ApiResponse<Void> stage(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody OpportunityRequests.StageAction request
    ) {
        opportunities.changeStage(actor, id, request);
        return ApiResponse.success(null);
    }

    /** 修改当前商机的停滞和宽限天数。 */
    @PatchMapping("/{id}/threshold")
    public ApiResponse<Void> threshold(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody OpportunityRequests.Threshold request
    ) {
        opportunities.updateThreshold(actor, id, request);
        return ApiResponse.success(null);
    }

    /** 添加当前商机的同团队协作人。 */
    @PostMapping("/{id}/collaborators")
    public ApiResponse<Void> addCollaborator(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody OpportunityRequests.CollaboratorWrite request
    ) {
        opportunities.addCollaborator(actor, id, request);
        return ApiResponse.success(null);
    }

    /** 移除当前商机的协作授权。 */
    @DeleteMapping("/{id}/collaborators/{collaboratorId}")
    public ApiResponse<Void> removeCollaborator(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Positive @PathVariable Long collaboratorId,
        @RequestParam @jakarta.validation.constraints.PositiveOrZero long version
    ) {
        opportunities.removeCollaborator(
            actor,
            id,
            new OpportunityRequests.CollaboratorRemove(version, collaboratorId)
        );
        return ApiResponse.success(null);
    }

    /** 查询当前商机的阶段历史。 */
    @GetMapping("/{id}/stage-history")
    public ApiResponse<List<OpportunityStageLogVO>> history(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id
    ) {
        return ApiResponse.success(opportunities.detail(actor, id).getStageHistory());
    }

    /** 查询权限范围内各阶段的数量和预计金额。 */
    @GetMapping("/funnel")
    public ApiResponse<List<OpportunityFunnelVO>> funnel(
        @AuthenticationPrincipal AuthPrincipal actor,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(
            required = false
        ) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) Long ownerId
    ) {
        LocalDateTime start = from == null ? null : from.atStartOfDay();
        LocalDateTime end = to == null ? null : to.plusDays(1).atStartOfDay();
        return ApiResponse.success(opportunities.funnel(actor, start, end, ownerId));
    }
}
