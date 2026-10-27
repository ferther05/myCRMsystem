package com.bishe.crm.module.contract.controller;

import com.bishe.crm.common.api.ApiResponse;
import com.bishe.crm.common.api.PageResult;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.contract.dto.ContractQuery;
import com.bishe.crm.module.contract.dto.ContractRequests;
import com.bishe.crm.module.contract.service.ContractService;
import com.bishe.crm.module.contract.vo.ContractDetailVO;
import com.bishe.crm.module.contract.vo.ContractSummaryVO;
import com.bishe.crm.module.contract.vo.ContractRegistrationVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Validated
public class ContractController {
    private final ContractService contracts;

    @GetMapping
    public ApiResponse<PageResult<ContractSummaryVO>> list(@AuthenticationPrincipal AuthPrincipal actor, @Valid ContractQuery query) {
        return ApiResponse.success(contracts.list(actor, query));
    }

    @GetMapping("/registration-opportunities")
    public ApiResponse<PageResult<ContractRegistrationVO>> registrationOpportunities(@AuthenticationPrincipal AuthPrincipal actor, @Valid ContractQuery query) {
        return ApiResponse.success(contracts.registrationOpportunities(actor, query));
    }

    @GetMapping("/{id}")
    public ApiResponse<ContractDetailVO> detail(@AuthenticationPrincipal AuthPrincipal actor, @Positive @PathVariable Long id) {
        return ApiResponse.success(contracts.detail(actor, id));
    }

    @PostMapping("/from-opportunity")
    public ApiResponse<Long> create(@AuthenticationPrincipal AuthPrincipal actor, @Valid @RequestBody ContractRequests.Create request) {
        return ApiResponse.success(contracts.createDraft(actor, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> edit(@AuthenticationPrincipal AuthPrincipal actor, @Positive @PathVariable Long id, @Valid @RequestBody ContractRequests.Edit request) {
        contracts.editDraft(actor, id, request); return ApiResponse.success(null);
    }

    @PostMapping("/{id}/attachments")
    public ApiResponse<Void> upload(@AuthenticationPrincipal AuthPrincipal actor, @Positive @PathVariable Long id,
                                    @RequestParam String materialType, @RequestParam(required = false) String note,
                                    @RequestPart("file") MultipartFile file) {
        contracts.upload(actor, id, materialType, note, file); return ApiResponse.success(null);
    }

    @GetMapping("/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AuthPrincipal actor, @Positive @PathVariable Long id,
                                             @Positive @PathVariable Long attachmentId) {
        Resource resource = contracts.download(actor, id, attachmentId);
        String name = resource.getFilename() == null ? "contract-file" : resource.getFilename();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<Void> submit(@AuthenticationPrincipal AuthPrincipal actor, @Positive @PathVariable Long id, @Valid @RequestBody ContractRequests.Submit request) {
        contracts.submit(actor, id, request); return ApiResponse.success(null);
    }

    @PostMapping("/{id}/review")
    public ApiResponse<Void> review(@AuthenticationPrincipal AuthPrincipal actor, @Positive @PathVariable Long id, @Valid @RequestBody ContractRequests.Review request) {
        contracts.review(actor, id, request); return ApiResponse.success(null);
    }

    @PatchMapping("/{id}/signing-progress")
    public ApiResponse<Void> signingProgress(@AuthenticationPrincipal AuthPrincipal actor, @Positive @PathVariable Long id,
                                             @Valid @RequestBody ContractRequests.SigningProgress request) {
        contracts.updateSigningProgress(actor, id, request); return ApiResponse.success(null);
    }
}
