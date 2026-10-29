/* 文件作用：提供应用及基础依赖的健康检查接口。 */
package com.bishe.crm.module.health.controller;

import com.bishe.crm.common.api.ApiResponse;
import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.module.health.service.InfrastructureHealthService;
import com.bishe.crm.module.health.vo.HealthVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供应用及基础依赖的健康检查接口。 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final InfrastructureHealthService healthService;

    /** 返回基础依赖健康状态并选择对应 HTTP 状态。 */
    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<HealthVO>> health() {
        HealthVO result = healthService.check();
        if ("UP".equals(result.status())) {
            return ResponseEntity.ok(ApiResponse.success(result));
        }
        ErrorCode error = ErrorCode.DEPENDENCY_UNAVAILABLE;
        return ResponseEntity.status(error.status()).body(
            ApiResponse.failure(error, error.message(), result)
        );
    }
}
