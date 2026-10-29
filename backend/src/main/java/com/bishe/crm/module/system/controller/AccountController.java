/* 文件作用：账号、团队、个人资料和内部账号申请接口。 */
package com.bishe.crm.module.system.controller;

import com.bishe.crm.common.api.*;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.auth.vo.LoginVO;
import com.bishe.crm.module.system.dto.*;
import com.bishe.crm.module.system.entity.AccountApplication;
import com.bishe.crm.module.system.service.AccountService;
import com.bishe.crm.module.system.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 账号、团队、个人资料和内部账号申请接口。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class AccountController {

    private final AccountService accounts;

    /** 更新本人允许修改的资料并刷新令牌。 */
    @PutMapping("/account/profile")
    public ApiResponse<LoginVO> profile(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Valid @RequestBody UserRequests.Profile request
    ) {
        return ApiResponse.success(accounts.profile(actor, request));
    }

    /** 按当前用户可见范围分页查询记录。 */
    @GetMapping("/users")
    public ApiResponse<PageResult<UserVO>> list(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Valid UserQuery query
    ) {
        return ApiResponse.success(accounts.list(actor, query));
    }

    /** 查询详情并检查当前用户的数据权限。 */
    @GetMapping("/users/{id}")
    public ApiResponse<UserVO> detail(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id
    ) {
        return ApiResponse.success(accounts.detail(actor, id));
    }

    /** 查询当前用户可见的团队。 */
    @GetMapping("/teams")
    public ApiResponse<List<TeamVO>> teams(@AuthenticationPrincipal AuthPrincipal actor) {
        return ApiResponse.success(accounts.teams(actor));
    }

    /** 管理员直接创建账号。 */
    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserVO> create(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Valid @RequestBody UserRequests.Create request
    ) {
        return ApiResponse.success(accounts.create(actor, request));
    }

    /** 管理员调整角色、团队或启停状态。 */
    @PutMapping("/users/{id}/access")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> access(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody UserRequests.Access request
    ) {
        accounts.access(actor, id, request);
        return ApiResponse.success(null);
    }

    /** 管理员重置初始密码并撤销旧会话。 */
    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> reset(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody UserRequests.Reset request
    ) {
        accounts.reset(actor, id, request);
        return ApiResponse.success(null);
    }

    /** 查询当前范围内的内部账号申请。 */
    @GetMapping("/account-applications")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<PageResult<ApplicationVO>> applications(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Valid PageQuery query
    ) {
        return ApiResponse.success(accounts.applications(actor, query));
    }

    /** 经理或管理员提交内部账号申请。 */
    @PostMapping("/account-applications")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<Long> apply(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Valid @RequestBody UserRequests.Apply request
    ) {
        return ApiResponse.success(accounts.apply(actor, request));
    }

    /** 管理员通过或驳回账号申请。 */
    @PostMapping("/account-applications/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> review(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody UserRequests.Review request
    ) {
        accounts.review(actor, id, request);
        return ApiResponse.success(null);
    }

    /** 修改并重新提交被驳回的申请。 */
    @PostMapping("/account-applications/{id}/resubmit")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<Void> resubmit(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @RequestParam @Min(0) long version
    ) {
        accounts.resubmit(actor, id, version);
        return ApiResponse.success(null);
    }
}
