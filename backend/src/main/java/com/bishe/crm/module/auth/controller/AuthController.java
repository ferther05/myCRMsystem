/* 文件作用：登录、当前身份、改密和退出接口。 */
package com.bishe.crm.module.auth.controller;

import com.bishe.crm.common.api.ApiResponse;
import com.bishe.crm.module.auth.dto.ChangePasswordRequest;
import com.bishe.crm.module.auth.dto.LoginRequest;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.auth.service.AuthService;
import com.bishe.crm.module.auth.vo.CurrentUserVO;
import com.bishe.crm.module.auth.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录、当前身份、改密和退出接口。 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;

    /** 接收账号密码并返回登录令牌。 */
    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(auth.login(request));
    }

    /** 返回当前登录用户资料。 */
    @GetMapping("/me")
    public ApiResponse<CurrentUserVO> me(@AuthenticationPrincipal AuthPrincipal user) {
        return ApiResponse.success(auth.me(user));
    }

    /** 修改密码并要求使用新密码重新登录。 */
    @PostMapping("/password")
    public ApiResponse<Void> password(
        @AuthenticationPrincipal AuthPrincipal user,
        @Valid @RequestBody ChangePasswordRequest request
    ) {
        auth.changePassword(user, request);
        return ApiResponse.success(null);
    }

    /** 撤销当前账号的登录会话。 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthPrincipal user) {
        auth.logout(user);
        return ApiResponse.success(null);
    }
}
