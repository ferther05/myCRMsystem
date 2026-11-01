/* 文件作用：合同登记、附件版本、内部核验和签署进度接口。 */
package com.bishe.crm.module.contract.controller;

import com.bishe.crm.common.api.ApiResponse;
import com.bishe.crm.common.api.PageResult;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.contract.dto.ContractQuery;
import com.bishe.crm.module.contract.dto.ContractRequests;
import com.bishe.crm.module.contract.service.ContractService;
import com.bishe.crm.module.contract.vo.ContractDetailVO;
import com.bishe.crm.module.contract.vo.ContractRegistrationVO;
import com.bishe.crm.module.contract.vo.ContractSummaryVO;
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

/** 合同登记、附件版本、内部核验和签署进度接口。 */
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Validated
public class ContractController {

    private final ContractService contracts;

    /** 按当前用户可见范围分页查询记录。 */
    @GetMapping
    public ApiResponse<PageResult<ContractSummaryVO>> list(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Valid ContractQuery query
    ) {
        return ApiResponse.success(contracts.list(actor, query));
    }

    /** 分页查询成交阶段的合同登记候选商机。 */
    @GetMapping("/registration-opportunities")
    public ApiResponse<PageResult<ContractRegistrationVO>> registrationOpportunities(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Valid ContractQuery query
    ) {
        return ApiResponse.success(contracts.registrationOpportunities(actor, query));
    }

    /** 查询详情并检查当前用户的数据权限。 */
    @GetMapping("/{id}")
    public ApiResponse<ContractDetailVO> detail(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id
    ) {
        return ApiResponse.success(contracts.detail(actor, id));
    }

    /** 根据成交商机创建合同登记草稿。 */
    @PostMapping("/from-opportunity")
    public ApiResponse<Long> create(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Valid @RequestBody ContractRequests.Create request
    ) {
        return ApiResponse.success(contracts.createDraft(actor, request));
    }

    /** 更新合同登记草稿。 */
    @PutMapping("/{id}")
    public ApiResponse<Void> edit(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody ContractRequests.Edit request
    ) {
        contracts.editDraft(actor, id, request);
        return ApiResponse.success(null);
    }

    /** 提交在线登记表供经理或管理员核对，不要求草稿文件。 */
    @PostMapping("/{id}/draft-submit")
    public ApiResponse<Void> submitDraft(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody ContractRequests.Submit request
    ) {
        contracts.submitDraft(actor, id, request);
        return ApiResponse.success(null);
    }

    /** 负责人撤回待审登记，回到草稿继续修改。 */
    @PostMapping("/{id}/draft-withdraw")
    public ApiResponse<Void> withdrawDraft(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody ContractRequests.WithdrawDraft request
    ) {
        contracts.withdrawDraft(actor, id, request);
        return ApiResponse.success(null);
    }

    /** 上传实际签署文件或补充资料，保留所有历史版本。 */
    @PostMapping("/{id}/attachments")
    public ApiResponse<Void> upload(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @RequestParam String materialType,
        @RequestParam(required = false) String note,
        @RequestPart("file") MultipartFile file
    ) {
        contracts.upload(actor, id, materialType, note, file);
        return ApiResponse.success(null);
    }

    /** 校验权限后下载指定合同附件。 */
    @GetMapping("/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> download(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Positive @PathVariable Long attachmentId
    ) {
        Resource resource = contracts.download(actor, id, attachmentId);
        String name = resource.getFilename() == null ? "contract-file" : resource.getFilename();
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header("X-Content-Type-Options", "nosniff")
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(name, StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .body(resource);
    }

    /** 提交登记资料供经理或管理员核验。 */
    @PostMapping("/{id}/submit")
    public ApiResponse<Void> submit(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody ContractRequests.Submit request
    ) {
        contracts.submit(actor, id, request);
        return ApiResponse.success(null);
    }

    /** 核验通过或退回合同登记。 */
    @PostMapping("/{id}/review")
    public ApiResponse<Void> review(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody ContractRequests.Review request
    ) {
        contracts.review(actor, id, request);
        return ApiResponse.success(null);
    }

    /** 更新合同签署节点并保留历史。 */
    @PatchMapping("/{id}/signing-progress")
    public ApiResponse<Void> signingProgress(
        @AuthenticationPrincipal AuthPrincipal actor,
        @Positive @PathVariable Long id,
        @Valid @RequestBody ContractRequests.SigningProgress request
    ) {
        contracts.updateSigningProgress(actor, id, request);
        return ApiResponse.success(null);
    }
}
