package com.bishe.crm.module.opportunity.controller;

import com.bishe.crm.common.api.ApiResponse;
import com.bishe.crm.common.api.PageResult;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.opportunity.dto.OpportunityQuery;
import com.bishe.crm.module.opportunity.dto.OpportunityRequests;
import com.bishe.crm.module.opportunity.service.OpportunityService;
import com.bishe.crm.module.opportunity.service.OpportunityActivityService;
import com.bishe.crm.module.customer.service.CustomerService;
import com.bishe.crm.module.customer.dto.CustomerRequests;
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

@RestController
@RequestMapping("/api/opportunities")
@RequiredArgsConstructor
@Validated
public class OpportunityController {
    private final OpportunityService opportunities;
    private final OpportunityActivityService activity;
    private final CustomerService customers;

    @GetMapping("/owners")
    public ApiResponse<java.util.List<java.util.Map<String,Object>>> owners(@AuthenticationPrincipal AuthPrincipal actor) {
        return ApiResponse.success(opportunities.owners(actor));
    }

    @PostMapping("/activity-scan")
    public ApiResponse<java.util.Map<String,Object>> scan(@AuthenticationPrincipal AuthPrincipal actor) {
        if (!"ADMIN".equals(actor.roleCode())) throw new com.bishe.crm.common.exception.BizException(com.bishe.crm.common.api.ErrorCode.FORBIDDEN);
        return ApiResponse.success(activity.scanNow(actor.id()));
    }

    @PostMapping("/{id}/evidence")
    public ApiResponse<Void> evidence(@AuthenticationPrincipal AuthPrincipal actor,@Positive @PathVariable Long id,@Valid @RequestBody OpportunityRequests.Evidence request) {
        opportunities.evidence(actor,id,request); return ApiResponse.success(null);
    }

    @PostMapping("/{id}/close-decision")
    public ApiResponse<Void> decide(@AuthenticationPrincipal AuthPrincipal actor,@Positive @PathVariable Long id,@Valid @RequestBody OpportunityRequests.PendingDecision request) {
        opportunities.decidePending(actor,id,request); return ApiResponse.success(null);
    }

    @GetMapping("/{id}/members")
    public ApiResponse<java.util.List<java.util.Map<String,Object>>> members(@AuthenticationPrincipal AuthPrincipal actor,@Positive @PathVariable Long id) {
        return ApiResponse.success(opportunities.members(actor,id));
    }

    @PostMapping("/{id}/follow-records")
    public ApiResponse<Long> follow(@AuthenticationPrincipal AuthPrincipal actor,@Positive @PathVariable Long id,@Valid @RequestBody CustomerRequests.FollowWrite request) {
        var row=opportunities.detail(actor,id);
        var bound=new CustomerRequests.FollowWrite(request.customerVersion(),id,request.contactId(),request.followMethod(),request.content(),request.resultCode(),request.substantiveResult(),request.nextAction(),request.nextActionAt(),request.followedAt());
        return ApiResponse.success(customers.addFollow(actor,row.getCustomerId(),bound));
    }

    @GetMapping
    public ApiResponse<PageResult<OpportunitySummaryVO>> list(@AuthenticationPrincipal AuthPrincipal actor,
                                                               @Valid OpportunityQuery query) {
        return ApiResponse.success(opportunities.list(actor, query));
    }

    @GetMapping("/{id}")
    public ApiResponse<OpportunityDetailVO> detail(@AuthenticationPrincipal AuthPrincipal actor,
                                                   @Positive @PathVariable Long id) {
        return ApiResponse.success(opportunities.detail(actor, id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Long> create(@AuthenticationPrincipal AuthPrincipal actor,
                                    @Valid @RequestBody OpportunityRequests.Create request) {
        return ApiResponse.success(opportunities.create(actor, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> edit(@AuthenticationPrincipal AuthPrincipal actor, @Positive @PathVariable Long id,
                                  @Valid @RequestBody OpportunityRequests.Edit request) {
        opportunities.edit(actor, id, request); return ApiResponse.success(null);
    }

    @PatchMapping("/{id}/stage")
    public ApiResponse<Void> stage(@AuthenticationPrincipal AuthPrincipal actor, @Positive @PathVariable Long id,
                                   @Valid @RequestBody OpportunityRequests.StageAction request) {
        opportunities.changeStage(actor, id, request); return ApiResponse.success(null);
    }

    @PatchMapping("/{id}/threshold")
    public ApiResponse<Void> threshold(@AuthenticationPrincipal AuthPrincipal actor, @Positive @PathVariable Long id,
                                       @Valid @RequestBody OpportunityRequests.Threshold request) {
        opportunities.updateThreshold(actor, id, request); return ApiResponse.success(null);
    }

    @PostMapping("/{id}/collaborators")
    public ApiResponse<Void> addCollaborator(@AuthenticationPrincipal AuthPrincipal actor, @Positive @PathVariable Long id,
                                             @Valid @RequestBody OpportunityRequests.CollaboratorWrite request) {
        opportunities.addCollaborator(actor, id, request); return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}/collaborators/{collaboratorId}")
    public ApiResponse<Void> removeCollaborator(@AuthenticationPrincipal AuthPrincipal actor, @Positive @PathVariable Long id,
                                                @Positive @PathVariable Long collaboratorId,
                                                @RequestParam @jakarta.validation.constraints.PositiveOrZero long version) {
        opportunities.removeCollaborator(actor, id, new OpportunityRequests.CollaboratorRemove(version, collaboratorId));
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/stage-history")
    public ApiResponse<List<OpportunityStageLogVO>> history(@AuthenticationPrincipal AuthPrincipal actor,
                                                            @Positive @PathVariable Long id) {
        return ApiResponse.success(opportunities.detail(actor, id).getStageHistory());
    }

    @GetMapping("/funnel")
    public ApiResponse<List<OpportunityFunnelVO>> funnel(@AuthenticationPrincipal AuthPrincipal actor,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long ownerId) {
        LocalDateTime start = from == null ? null : from.atStartOfDay();
        LocalDateTime end = to == null ? null : to.plusDays(1).atStartOfDay();
        return ApiResponse.success(opportunities.funnel(actor, start, end, ownerId));
    }
}
