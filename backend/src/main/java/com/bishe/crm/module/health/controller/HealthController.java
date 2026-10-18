package com.bishe.crm.module.health.controller;

import com.bishe.crm.common.api.ApiResponse;
import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.module.health.service.InfrastructureHealthService;
import com.bishe.crm.module.health.vo.HealthVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HealthController {
    private final InfrastructureHealthService healthService;

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<HealthVO>> health() {
        HealthVO result = healthService.check();
        if ("UP".equals(result.status())) {
            return ResponseEntity.ok(ApiResponse.success(result));
        }
        ErrorCode error = ErrorCode.DEPENDENCY_UNAVAILABLE;
        return ResponseEntity.status(error.status())
                .body(ApiResponse.failure(error, error.message(), result));
    }
}
