/* 文件作用：角色工作台只读汇总和分页入口。 */
package com.bishe.crm.module.workbench.controller;

import com.bishe.crm.common.api.*;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.workbench.dto.WorkbenchQuery;
import com.bishe.crm.module.workbench.service.WorkbenchService;
import com.bishe.crm.module.workbench.vo.WorkbenchViews.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class WorkbenchController {
    private final WorkbenchService service;
    /** WORK-01：当前角色的真实工作汇总。 */
    @GetMapping("/overview")
    public ApiResponse<Overview> overview(@AuthenticationPrincipal AuthPrincipal a) { return ApiResponse.success(service.overview(a)); }
    /** WORK-02：同一授权口径下的十条分页清单。 */
    @GetMapping("/items")
    public ApiResponse<PageResult<Item>> items(@AuthenticationPrincipal AuthPrincipal a,@Valid WorkbenchQuery q) { return ApiResponse.success(service.items(a,q)); }
}
