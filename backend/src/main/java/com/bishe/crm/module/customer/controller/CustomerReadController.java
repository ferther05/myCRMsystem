package com.bishe.crm.module.customer.controller;

import com.bishe.crm.common.api.*;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.customer.service.CustomerReadService;
import com.bishe.crm.module.customer.vo.CustomerSummaryVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/customers") @RequiredArgsConstructor @Validated
public class CustomerReadController {
    private final CustomerReadService customers;

    @GetMapping
    public ApiResponse<PageResult<CustomerSummaryVO>> list(@AuthenticationPrincipal AuthPrincipal actor,
            @Valid PageQuery query, @RequestParam(defaultValue="PRIVATE") @Pattern(regexp="PRIVATE|PUBLIC") String sea) {
        return ApiResponse.success(customers.list(actor, query, sea));
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerSummaryVO> detail(@AuthenticationPrincipal AuthPrincipal actor,
                                                @Positive @PathVariable Long id) {
        return ApiResponse.success(customers.detail(actor, id));
    }
}
