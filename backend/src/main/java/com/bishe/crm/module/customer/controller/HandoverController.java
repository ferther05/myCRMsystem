/* 文件作用：管理员同团队批量交接入口；服务端再次验证角色、归属与版本。 */
package com.bishe.crm.module.customer.controller;

import com.bishe.crm.common.api.ApiResponse;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.customer.dto.HandoverRequests.*;
import com.bishe.crm.module.customer.service.HandoverService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers/handover")
@RequiredArgsConstructor
@Validated
public class HandoverController {
    private final HandoverService service;
    @GetMapping("/owners")
    public ApiResponse<List<Map<String,Object>>> owners(@AuthenticationPrincipal AuthPrincipal a) {return ApiResponse.success(service.owners(a));}
    @GetMapping("/candidates")
    public ApiResponse<List<Map<String,Object>>> candidates(@AuthenticationPrincipal AuthPrincipal a,@Positive @RequestParam Long ownerId) {return ApiResponse.success(service.candidates(a,ownerId));}
    @PostMapping("/preview")
    public ApiResponse<Map<String,Object>> preview(@AuthenticationPrincipal AuthPrincipal a,@Valid @RequestBody Selection r) {return ApiResponse.success(service.preview(a,r));}
    @PostMapping
    public ApiResponse<Map<String,Object>> execute(@AuthenticationPrincipal AuthPrincipal a,@Valid @RequestBody Execute r) {return ApiResponse.success(service.execute(a,r));}
}
