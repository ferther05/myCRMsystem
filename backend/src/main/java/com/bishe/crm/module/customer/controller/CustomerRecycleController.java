/* 文件作用：回收预览、默认关闭开关、手动预警及客户延期审核接口。 */
package com.bishe.crm.module.customer.controller;

import com.bishe.crm.common.api.*;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.customer.dto.RecycleRequests;
import com.bishe.crm.module.customer.service.CustomerRecycleService;
import com.bishe.crm.module.customer.vo.RecycleViews;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 身份范围与状态转换由服务层复核。 */
@RestController @RequestMapping("/api/recycle") @RequiredArgsConstructor @Validated
public class CustomerRecycleController {
    private final CustomerRecycleService recycle;
    /** RECYCLE-01：管理员只读预览。 */
    @GetMapping("/preview")
    public ApiResponse<PageResult<RecycleViews.Row>> preview(@AuthenticationPrincipal AuthPrincipal a,@Valid RecycleRequests.Query q) {return ApiResponse.success(recycle.preview(a,q));}
    /** RECYCLE-02：读取默认关闭开关。 */
    @GetMapping("/setting")
    public ApiResponse<RecycleViews.Setting> setting(@AuthenticationPrincipal AuthPrincipal a) {return ApiResponse.success(recycle.setting(a));}
    /** RECYCLE-03：管理员显式启用/关闭。 */
    @PutMapping("/setting")
    public ApiResponse<Void> setting(@AuthenticationPrincipal AuthPrincipal a,@Valid @RequestBody RecycleRequests.Setting r) {recycle.setting(a,r);return ApiResponse.success(null);}
    /** RECYCLE-04：当天扫描，关闭时不改客户。 */
    @PostMapping("/scan")
    public ApiResponse<Map<String,Object>> scan(@AuthenticationPrincipal AuthPrincipal a) {return ApiResponse.success(recycle.scan(a));}
    /** RECYCLE-05：客户规则与延期历史。 */
    @GetMapping("/customers/{id}")
    public ApiResponse<RecycleViews.Detail> detail(@AuthenticationPrincipal AuthPrincipal a,@Positive @PathVariable Long id) {return ApiResponse.success(recycle.detail(a,id));}
    /** RECYCLE-06：管理员发送单客户预警。 */
    @PostMapping("/customers/{id}/warning")
    public ApiResponse<Void> warning(@AuthenticationPrincipal AuthPrincipal a,@Positive @PathVariable Long id,@Valid @RequestBody RecycleRequests.Version r) {recycle.warn(a,id,r.version());return ApiResponse.success(null);}
    /** RECYCLE-07：当前负责人申请延期。 */
    @PostMapping("/customers/{id}/appeals")
    public ApiResponse<Long> appeal(@AuthenticationPrincipal AuthPrincipal a,@Positive @PathVariable Long id,@Valid @RequestBody RecycleRequests.Appeal r) {return ApiResponse.success(recycle.appeal(a,id,r));}
    /** RECYCLE-08：非申请人管理者审核。 */
    @PostMapping("/appeals/{id}/review")
    public ApiResponse<Void> review(@AuthenticationPrincipal AuthPrincipal a,@Positive @PathVariable Long id,@Valid @RequestBody RecycleRequests.Review r) {recycle.review(a,id,r);return ApiResponse.success(null);}
}
