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

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;

    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(auth.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserVO> me(@AuthenticationPrincipal AuthPrincipal user) {
        return ApiResponse.success(auth.me(user));
    }

    @PostMapping("/password")
    public ApiResponse<Void> password(@AuthenticationPrincipal AuthPrincipal user,
                                      @Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(user, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthPrincipal user) {
        auth.logout(user);
        return ApiResponse.success(null);
    }
}
