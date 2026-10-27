package com.bishe.crm.module.customer.controller;

import com.bishe.crm.common.api.ApiResponse;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.customer.dto.CustomerRequests;
import com.bishe.crm.module.customer.service.CustomerDictionaryService;
import com.bishe.crm.module.customer.vo.CustomerDictionaryVO;
import com.bishe.crm.module.system.vo.UserVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api") @RequiredArgsConstructor @Validated
public class CustomerDictionaryController {
    private final CustomerDictionaryService dictionaries;
    @GetMapping("/customer-dictionaries")
    public ApiResponse<List<CustomerDictionaryVO>> list() { return ApiResponse.success(dictionaries.list()); }
    @GetMapping("/customers/owners")
    public ApiResponse<List<UserVO>> owners(@AuthenticationPrincipal AuthPrincipal actor) { return ApiResponse.success(dictionaries.owners(actor)); }
    @PostMapping("/customer-dictionaries")
    public ApiResponse<Void> create(@AuthenticationPrincipal AuthPrincipal actor,@Valid @RequestBody CustomerRequests.DictionaryWrite request) {
        dictionaries.create(actor,request); return ApiResponse.success(null);
    }
    @PutMapping("/customer-dictionaries/{id}")
    public ApiResponse<Void> edit(@AuthenticationPrincipal AuthPrincipal actor,@Positive @PathVariable Long id,
                                @Valid @RequestBody CustomerRequests.DictionaryEdit request) {
        dictionaries.edit(actor,id,request); return ApiResponse.success(null);
    }
}
