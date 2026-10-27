package com.bishe.crm.module.contract.service;

import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.common.api.PageResult;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.auth.security.DataScope;
import com.bishe.crm.module.contract.dto.ContractQuery;
import com.bishe.crm.module.contract.dto.ContractRequests;
import com.bishe.crm.module.contract.entity.Contract;
import com.bishe.crm.module.contract.entity.ContractAttachment;
import com.bishe.crm.module.contract.mapper.ContractMapper;
import com.bishe.crm.module.contract.vo.ContractDetailVO;
import com.bishe.crm.module.contract.vo.ContractSummaryVO;
import com.bishe.crm.module.contract.vo.ContractRegistrationVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bishe.crm.module.opportunity.entity.Opportunity;
import com.bishe.crm.module.opportunity.mapper.OpportunityMapper;
import com.bishe.crm.module.opportunity.vo.OpportunitySummaryVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 合同登记切片：保存管理事实和签署资料，不生成合同正文或电子签名。 */
@Service
@RequiredArgsConstructor
public class ContractService {
    private static final Set<String> EDITABLE = Set.of("DRAFT", "RETURNED");
    private static final Set<String> SIGNING_PROGRESS = Set.of("INTERNAL_HANDLING", "WAIT_OUR_SIGN", "WAIT_COUNTERPARTY_SIGN", "BOTH_SIGNED", "EXCEPTION", "WITHDRAWN");
    private static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".doc", ".docx");

    private final ContractMapper contracts;
    private final OpportunityMapper opportunities;
    private final com.bishe.crm.module.opportunity.service.OpportunityService opportunityService;
    private final com.bishe.crm.module.customer.mapper.CustomerMapper customers;
    private final ObjectMapper json;
    private final Clock businessClock;
    @Value("${crm.files.root:../output/contract-files}") private String filesRoot;

    @Transactional(readOnly = true)
    public PageResult<ContractSummaryVO> list(AuthPrincipal actor, ContractQuery query) {
        requireUsable(actor);
        var page = contracts.list(query.toPage(), DataScope.of(actor), query);
        page.getRecords().forEach(row -> decorate(row, actor));
        return PageResult.from(page);
    }

    @Transactional(readOnly = true)
    public PageResult<ContractRegistrationVO> registrationOpportunities(AuthPrincipal actor, ContractQuery query) {
        requireUsable(actor);
        var page = contracts.registrationOpportunities(new Page<>(query.getPage(), query.getSize()), DataScope.of(actor), query);
        page.getRecords().forEach(row -> row.setCanUpload(row.getContractId() == null || EDITABLE.contains(row.getContractStatus())));
        return PageResult.from(page);
    }

    @Transactional(readOnly = true)
    public ContractDetailVO detail(AuthPrincipal actor, Long id) {
        ContractSummaryVO summary = visible(actor, id);
        ContractDetailVO detail = new ContractDetailVO();
        BeanUtils.copyProperties(summary, detail);
        Contract entity = contracts.find(id);
        detail.setDescription(entity == null ? null : entity.getDescription());
        detail.setAttachments(contracts.attachments(id));
        detail.getAttachments().forEach(item -> item.setDownloadUrl("/api/contracts/" + id + "/attachments/" + item.getId() + "/download"));
        detail.setOperationHistory(contracts.operationHistory(id));
        detail.setSigningHistory(contracts.signingHistory(id));
        return detail;
    }

    @Transactional
    public Long createDraft(AuthPrincipal actor, ContractRequests.Create request) {
        requireUsable(actor);
        OpportunitySummaryVO opportunity = opportunities.summary(request.opportunityId(), DataScope.of(actor));
        if (opportunity == null) throw new BizException(ErrorCode.NOT_FOUND);
        boolean manageable = "ADMIN".equals(actor.roleCode())
                || Objects.equals(actor.id(), opportunity.getOwnerId())
                || ("MANAGER".equals(actor.roleCode()) && Objects.equals(actor.teamId(), opportunity.getTeamId()));
        if (!manageable) throw new BizException(ErrorCode.NOT_FOUND);
        // 与阶段推进遵循客户→商机锁序，避免重复点击或并发推进后创建过期草稿。
        if (customers.lock(opportunity.getCustomerId()) == null) throw new BizException(ErrorCode.NOT_FOUND);
        Opportunity locked = opportunities.lock(opportunity.getId());
        if (locked == null || !Objects.equals(locked.getOwnerId(), opportunity.getOwnerId())
                || !Objects.equals(locked.getTeamId(), opportunity.getTeamId())) throw new BizException(ErrorCode.CONFLICT);
        if (!"DEAL".equals(locked.getStageCode())) throw new BizException(ErrorCode.CONTRACT_RULE);
        if (contracts.currentByOpportunity(request.opportunityId()) != null) throw new BizException(ErrorCode.CONFLICT);
        LocalDateTime now = now();
        Contract contract = new Contract();
        contract.setContractNo("CRM-" + now.toLocalDate().toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        contract.setCustomerId(locked.getCustomerId()); contract.setOpportunityId(locked.getId());
        contract.setOwnerId(locked.getOwnerId()); contract.setTeamId(locked.getTeamId());
        contract.setContractName(locked.getOpportunityName() + "合同登记");
        contract.setContractAmount(scale(locked.getExpectedAmount())); contract.setOutstandingAmount(scale(locked.getExpectedAmount()));
        contract.setCreatedBy(actor.id()); contract.setCreatedAt(now); contract.setUpdatedAt(now);
        if (contracts.insert(contract) != 1) throw new BizException(ErrorCode.CONFLICT);
        contracts.insertSigningHistory(contract.getId(), null, "INTERNAL_HANDLING", actor.id(), actor.realName(), actor.roleCode(), actor.teamId(), now, "创建合同登记，开始内部办理", false);
        audit(actor, contract.getId(), "CONTRACT_DRAFT_CREATED", null, snapshot(contract), "从成交商机创建合同登记草稿");
        return contract.getId();
    }

    @Transactional
    public void editDraft(AuthPrincipal actor, Long id, ContractRequests.Edit request) {
        requireUsable(actor);
        Contract contract = writable(actor, id, request.version(), false);
        if (!EDITABLE.contains(contract.getContractStatus())) throw new BizException(ErrorCode.CONTRACT_RULE);
        String before = snapshot(contract);
        contract.setContractName(request.contractName().trim()); contract.setExternalContractNo(trim(request.externalContractNo()));
        contract.setBusinessType(trim(request.businessType())); contract.setOurPartyName(trim(request.ourPartyName()));
        contract.setCounterpartyName(trim(request.counterpartyName())); contract.setDescription(trim(request.description()));
        contract.setContractAmount(scale(request.contractAmount())); contract.setSignedAt(request.signedAt()); contract.setExpireDate(request.expireDate());
        if (contract.getExpireDate() != null && contract.getSignedAt() != null && contract.getExpireDate().isBefore(contract.getSignedAt())) throw new BizException(ErrorCode.VALIDATION_ERROR);
        if (contracts.updateDraft(contract, now()) != 1) throw new BizException(ErrorCode.CONFLICT);
        audit(actor, id, "CONTRACT_DRAFT_UPDATED", before, snapshot(contracts.find(id)), "修改合同登记信息");
    }

    @Transactional
    public void upload(AuthPrincipal actor, Long id, String materialType, String note, MultipartFile file) {
        requireUsable(actor);
        Contract contract = writable(actor, id, null, false);
        if (!EDITABLE.contains(contract.getContractStatus())) throw new BizException(ErrorCode.CONTRACT_RULE);
        if (file == null || file.isEmpty() || file.getSize() > 20 * 1024 * 1024L) throw new BizException(ErrorCode.CONTRACT_FILE_INVALID);
        String type = trim(materialType);
        if (!Set.of("SIGNED_CONTRACT", "SUPPLEMENT").contains(type)) throw new BizException(ErrorCode.CONTRACT_FILE_INVALID);
        String original = safeFileName(file.getOriginalFilename()); String lower = original.toLowerCase(Locale.ROOT);
        String extension = ALLOWED_EXTENSIONS.stream().filter(lower::endsWith).findFirst().orElse("");
        if (extension.isEmpty() || (file.getContentType() != null && !ALLOWED_TYPES.contains(file.getContentType()))) throw new BizException(ErrorCode.CONTRACT_FILE_INVALID);
        Path root = Path.of(filesRoot).toAbsolutePath().normalize(); Path folder = root.resolve("contract-" + id).normalize();
        Path stored = folder.resolve(UUID.randomUUID().toString().replace("-", "") + extension).normalize();
        if (!folder.startsWith(root) || !stored.startsWith(root)) throw new BizException(ErrorCode.CONTRACT_FILE_INVALID);
        try {
            Files.createDirectories(folder); file.transferTo(stored);
            ContractAttachment attachment = new ContractAttachment();
            LocalDateTime now = now(); attachment.setBizId(id); attachment.setMaterialType(type); attachment.setVersionNo(contracts.nextAttachmentVersion(id, type));
            attachment.setFileName(original); attachment.setStoragePath(stored.toString()); attachment.setFileSize(file.getSize()); attachment.setUploadedBy(actor.id()); attachment.setUploadedAt(now); attachment.setCredentialNote(trim(note));
            if (contracts.insertAttachment(attachment) != 1) throw new BizException(ErrorCode.CONFLICT);
            audit(actor, id, "CONTRACT_FILE_UPLOADED", null, "{\"attachmentId\":" + attachment.getId() + ",\"versionNo\":" + attachment.getVersionNo() + "}", "上传合同资料版本");
        } catch (IOException e) {
            try { Files.deleteIfExists(stored); } catch (IOException ignored) { }
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    @Transactional
    public void submit(AuthPrincipal actor, Long id, ContractRequests.Submit request) {
        requireUsable(actor);
        Contract contract = writable(actor, id, request.version(), false);
        if (contracts.latestSignedAttachment(id) == null || !"PENDING".equals(contracts.latestSignedAttachmentStatus(id))) throw new BizException(ErrorCode.CONTRACT_REVIEW_REQUIRED);
        requireCompleteDetails(contract);
        LocalDateTime now = now(); String before = snapshot(contract);
        if ("DRAFT".equals(contract.getContractStatus())) {
            if (contracts.submitSignedForReview(id, contract.getVersion(), now) != 1) throw new BizException(ErrorCode.CONFLICT);
        } else if ("RETURNED".equals(contract.getContractStatus())) {
            if (contracts.resubmitReturned(id, contract.getVersion(), now) != 1) throw new BizException(ErrorCode.CONFLICT);
        } else throw new BizException(ErrorCode.CONTRACT_RULE);
        audit(actor, id, "CONTRACT_SUBMITTED", before, snapshot(contracts.find(id)), "双方签署文件已提交经理核验");
    }

    @Transactional
    public void updateSigningProgress(AuthPrincipal actor, Long id, ContractRequests.SigningProgress request) {
        requireUsable(actor);
        ContractSummaryVO visible = visible(actor, id);
        boolean admin = "ADMIN".equals(actor.roleCode());
        if (!admin && !Boolean.TRUE.equals(visible.getCanUpdateSigningProgress())) throw new BizException(ErrorCode.FORBIDDEN);
        Contract contract = contracts.lock(id);
        if (contract == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (!Objects.equals(request.version(), contract.getVersion())) throw new BizException(ErrorCode.CONFLICT);
        if (!"DRAFT".equals(contract.getContractStatus())) throw new BizException(ErrorCode.CONTRACT_RULE);
        String from = contract.getSigningProgress(), to = request.targetProgress();
        if (!SIGNING_PROGRESS.contains(from) || "WITHDRAWN".equals(from) || from.equals(to)) throw new BizException(ErrorCode.CONTRACT_RULE);
        if ("BOTH_SIGNED".equals(to)) throw new BizException(ErrorCode.CONTRACT_RULE);
        if ("WITHDRAWN".equals(to) && contracts.signedAttachmentCount(id) > 0) throw new BizException(ErrorCode.CONTRACT_RULE);
        if (!admin && !allowedSigningTransition(from, to)) throw new BizException(ErrorCode.CONTRACT_RULE);
        if (!admin && "EXCEPTION".equals(from) && !"INTERNAL_HANDLING".equals(to)) throw new BizException(ErrorCode.CONTRACT_RULE);
        LocalDateTime now = now();
        if (contracts.updateSigningProgress(id, contract.getVersion(), from, to, now) != 1) throw new BizException(ErrorCode.CONFLICT);
        contracts.insertSigningHistory(id, from, to, actor.id(), actor.realName(), actor.roleCode(), actor.teamId(), now, request.reason().trim(), admin);
        audit(actor, id, "CONTRACT_SIGNING_PROGRESS_CHANGED", snapshot(contract), snapshot(contracts.find(id)), request.reason().trim());
    }

    @Transactional
    public void review(AuthPrincipal actor, Long id, ContractRequests.Review request) {
        requireUsable(actor);
        if (!Set.of("ADMIN", "MANAGER").contains(actor.roleCode())) throw new BizException(ErrorCode.FORBIDDEN);
        Contract initial = contracts.find(id);
        if (initial == null) throw new BizException(ErrorCode.NOT_FOUND);
        customers.lock(initial.getCustomerId());
        Contract contract = writable(actor, id, request.version(), true);
        if (!"PENDING_REVIEW".equals(contract.getContractStatus()) || ("MANAGER".equals(actor.roleCode()) && !Objects.equals(actor.teamId(), contract.getTeamId()))) throw new BizException(ErrorCode.FORBIDDEN);
        if (Objects.equals(actor.id(), contract.getOwnerId())) throw new BizException(ErrorCode.FORBIDDEN);
        Long attachmentId = contracts.latestSignedAttachment(id);
        if (attachmentId == null || !"PENDING".equals(contracts.latestSignedAttachmentStatus(id))) throw new BizException(ErrorCode.CONTRACT_REVIEW_REQUIRED);
        LocalDateTime now = now(); String before = snapshot(contract);
        if (!request.pass()) {
            if (contracts.updateStatus(id, contract.getVersion(), "PENDING_REVIEW", "RETURNED", null, now) != 1) throw new BizException(ErrorCode.CONFLICT);
            contracts.confirmAttachment(attachmentId, "REJECTED", request.opinion().trim(), actor.id(), now);
            audit(actor, id, "CONTRACT_REVIEW_RETURNED", before, snapshot(contracts.find(id)), request.opinion().trim()); return;
        }
        requireCompleteDetails(contract);
        Opportunity opportunity = opportunities.lock(contract.getOpportunityId());
        if (opportunity == null || !"DEAL".equals(opportunity.getStageCode())) throw new BizException(ErrorCode.CONTRACT_RULE);
        if (opportunities.markSignedConfirmed(opportunity.getId(), opportunity.getVersion(), now) != 1) throw new BizException(ErrorCode.CONFLICT);
        if (contracts.approveSignedForReview(id, contract.getVersion(), contract.getSignedAt(), now) != 1) throw new BizException(ErrorCode.CONFLICT);
        contracts.insertSigningHistory(id, "WAIT_COUNTERPARTY_SIGN", "BOTH_SIGNED", actor.id(), actor.realName(), actor.roleCode(), actor.teamId(), now, "签署资料与登记信息核验通过", false);
        contracts.confirmAttachment(attachmentId, "PASSED", request.opinion().trim(), actor.id(), now);
        contracts.protectCustomer(contract.getCustomerId(), now);
        audit(actor, id, "CONTRACT_REVIEW_PASSED", before, snapshot(contracts.find(id)), request.opinion().trim());
        opportunityService.advanceFromBusiness(opportunity.getId());
    }

    public Resource download(AuthPrincipal actor, Long id, Long attachmentId) {
        requireUsable(actor); visible(actor, id);
        ContractAttachment attachment = contracts.attachment(attachmentId, id);
        if (attachment == null) throw new BizException(ErrorCode.NOT_FOUND);
        try {
            Path root = Path.of(filesRoot).toAbsolutePath().normalize(); Path file = Path.of(attachment.getStoragePath()).toAbsolutePath().normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new BizException(ErrorCode.NOT_FOUND);
            return new UrlResource(file.toUri());
        } catch (IOException e) { throw new BizException(ErrorCode.NOT_FOUND); }
    }

    private ContractSummaryVO visible(AuthPrincipal actor, Long id) { ContractSummaryVO row = contracts.summary(id, DataScope.of(actor)); if (row == null) throw new BizException(ErrorCode.NOT_FOUND); decorate(row, actor); return row; }
    private Contract writable(AuthPrincipal actor, Long id, Long version, boolean management) { ContractSummaryVO row = visible(actor, id); if (!Boolean.TRUE.equals(row.getCanEdit()) && !(management && Boolean.TRUE.equals(row.getCanReview()))) throw new BizException(ErrorCode.FORBIDDEN); Contract c = contracts.lock(id); if (c == null) throw new BizException(ErrorCode.NOT_FOUND); if (version != null && !Objects.equals(version, c.getVersion())) throw new BizException(ErrorCode.CONFLICT); return c; }
    private void decorate(ContractSummaryVO row, AuthPrincipal actor) {
        boolean admin = "ADMIN".equals(actor.roleCode());
        boolean manager = admin || ("MANAGER".equals(actor.roleCode()) && Objects.equals(actor.teamId(), row.getTeamId()));
        boolean owner = Objects.equals(actor.id(), row.getOwnerId());
        boolean editable = (owner || manager) && EDITABLE.contains(row.getContractStatus());
        boolean mutableProgress = "DRAFT".equals(row.getContractStatus())
                && !"BOTH_SIGNED".equals(row.getSigningProgress()) && !"WITHDRAWN".equals(row.getSigningProgress());
        row.setCanEdit(editable && !"WITHDRAWN".equals(row.getSigningProgress()));
        row.setCanSubmit(editable && (("DRAFT".equals(row.getContractStatus()) && "WAIT_COUNTERPARTY_SIGN".equals(row.getSigningProgress()))
                || ("RETURNED".equals(row.getContractStatus()) && "WAIT_COUNTERPARTY_SIGN".equals(row.getSigningProgress()))));
        row.setCanReview(manager && "PENDING_REVIEW".equals(row.getContractStatus()) && !owner);
        row.setCanUpdateSigningProgress(mutableProgress && (owner || ("MANAGER".equals(actor.roleCode()) && manager)));
        row.setCanCorrectSigningProgress("DRAFT".equals(row.getContractStatus()) && admin && !"WITHDRAWN".equals(row.getSigningProgress()));
    }
    private boolean allowedSigningTransition(String from, String to) {
        return switch (from) {
            case "INTERNAL_HANDLING" -> Set.of("WAIT_OUR_SIGN", "EXCEPTION", "WITHDRAWN").contains(to);
            case "WAIT_OUR_SIGN" -> Set.of("WAIT_COUNTERPARTY_SIGN", "EXCEPTION", "WITHDRAWN").contains(to);
            case "WAIT_COUNTERPARTY_SIGN" -> Set.of("EXCEPTION", "WITHDRAWN").contains(to);
            case "EXCEPTION" -> "INTERNAL_HANDLING".equals(to);
            default -> false;
        };
    }
    private void audit(AuthPrincipal actor, Long id, String action, String before, String after, String reason) { contracts.audit(id, action, actor.id(), actor.teamId(), now(), before, after, reason); }
    private void requireUsable(AuthPrincipal actor) { if (actor == null || actor.mustChangePassword()) throw new BizException(ErrorCode.PASSWORD_CHANGE_REQUIRED); }
    private void requireCompleteDetails(Contract c) {
        if (trim(c.getContractNo()) == null || trim(c.getOurPartyName()) == null || trim(c.getCounterpartyName()) == null
                || c.getContractAmount() == null || c.getSignedAt() == null) throw new BizException(ErrorCode.CONTRACT_DETAILS_REQUIRED);
    }
    private LocalDateTime now() { return LocalDateTime.now(businessClock); }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private BigDecimal scale(BigDecimal value) { if (value == null || value.signum() < 0 || value.scale() > 2) throw new BizException(ErrorCode.VALIDATION_ERROR); return value.setScale(2); }
    private String safeFileName(String name) { String value = name == null ? "contract-file" : name.replace('\\', '/'); value = value.substring(value.lastIndexOf('/') + 1).trim(); return value.isBlank() ? "contract-file" : value.length() > 255 ? value.substring(0, 255) : value; }
    private String snapshot(Contract c) { try { Map<String,Object> value = new LinkedHashMap<>(); value.put("id", c.getId()); value.put("contractNo", c.getContractNo()); value.put("status", c.getContractStatus()); value.put("amount", c.getContractAmount()); value.put("version", c.getVersion()); return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("无法记录合同审计", e); } }
}
