/* 文件作用：客户分类字典和可分配负责人的查询维护接口。 */
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

/** 客户分类字典和可分配负责人的查询维护接口。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class CustomerDictionaryController {

    private final CustomerDictionaryService dictionaries;

    /** 按当前用户可见范围分页查询记录。 */
    @GetMapping("/customer-dictionaries")
    public ApiResponse<List<CustomerDictionaryVO>> list() {
        return ApiResponse.success(dictionaries.list());
    }

    /** 查询可作为负责人的启用账号。 */
    @GetMapping("/customers/owners")
    public ApiResponse<List<UserVO>> owners(@AuthenticationPrincipal AuthPrincipal actor) {
        return ApiResponse.success(dictionaries.owners(actor));
    }

    /** 新增客户分类字典项。 */
    @PostMapping("/customer-dictionaries")
    public ApiResponse<Void> create(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Valid @RequestBody CustomerRequests.DictionaryWrite request
    ) {
        dictionaries.create(actor, request);
        return ApiResponse.success(null);
    }

    /** 更新字典项名称、顺序或启停状态。 */
    @PutMapping("/customer-dictionaries/{id}")
    public ApiResponse<Void> edit(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody CustomerRequests.DictionaryEdit request
    ) {
        dictionaries.edit(actor, id, request);
        return ApiResponse.success(null);
    }
}
