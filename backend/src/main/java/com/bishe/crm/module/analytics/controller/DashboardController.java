/* 文件作用：数据看板只读接口，认证范围由后端身份决定。 */
package com.bishe.crm.module.analytics.controller;

import com.bishe.crm.common.api.ApiResponse;
import com.bishe.crm.module.analytics.dto.DashboardQuery;
import com.bishe.crm.module.analytics.service.DashboardService;
import com.bishe.crm.module.analytics.vo.DashboardViews;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/analytics") @RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboard;
    /** 返回当前授权范围的数据汇总，刷新参数只绕过缓存，不改变权限。 */
    @GetMapping("/overview") public ApiResponse<DashboardViews.Result> overview(@AuthenticationPrincipal AuthPrincipal actor,@Valid DashboardQuery query) {return ApiResponse.success(dashboard.overview(actor,query));}
    /** 仅管理员按需读取当前资源分配与治理信息。 */
    @GetMapping("/admin-resources")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DashboardViews.AdminResources> adminResources(
        @AuthenticationPrincipal AuthPrincipal actor
    ) {
        return ApiResponse.success(dashboard.adminResources(actor));
    }
    /** 返回可选负责人，不提供任意团队或用户枚举。 */
    @GetMapping("/owners") public ApiResponse<List<DashboardViews.Owner>> owners(@AuthenticationPrincipal AuthPrincipal actor) {return ApiResponse.success(dashboard.owners(actor));}
}
