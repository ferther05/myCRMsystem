/* 文件作用：编排合同登记、资料版本、签署进度与核验事务。 */
package com.bishe.crm.module.contract.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.bishe.crm.module.contract.storage.ContractFileStorage;
import com.bishe.crm.module.contract.vo.ContractDetailVO;
import com.bishe.crm.module.contract.vo.ContractRegistrationVO;
import com.bishe.crm.module.contract.vo.ContractSummaryVO;
import com.bishe.crm.module.opportunity.entity.Opportunity;
import com.bishe.crm.module.opportunity.mapper.OpportunityMapper;
import com.bishe.crm.module.opportunity.vo.OpportunitySummaryVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 合同登记切片：保存管理事实和签署资料，不生成合同正文或电子签名。 */
@Service
@RequiredArgsConstructor
public class ContractService {

    private static final Set<String> EDITABLE = Set.of("DRAFT", "RETURNED");
    private static final Set<String> NORMAL_PROGRESS = Set.of(
        "DRAFT_GENERATED",
        "DRAFT_REVIEW",
        "SIGNED_UPLOAD",
        "FILE_REVIEW",
        "FILE_CONFIRMED"
    );
    private static final Set<String> PAUSED = Set.of("EXCEPTION", "WITHDRAWN");
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".doc", ".docx");

    private final ContractMapper contracts;
    private final OpportunityMapper opportunities;
    private final com.bishe.crm.module.opportunity.service.OpportunityService opportunityService;
    private final com.bishe.crm.module.customer.mapper.CustomerMapper customers;
    private final ObjectMapper json;
    private final Clock businessClock;

    private final ContractFileStorage fileStorage;
    private final PaymentService paymentService;

    /** 按当前用户可见范围分页查询记录。 */
    @Transactional(readOnly = true)
    public PageResult<ContractSummaryVO> list(AuthPrincipal actor, ContractQuery query) {
        requireUsable(actor);
        var page = contracts.list(query.toPage(), DataScope.of(actor), query);
        page.getRecords().forEach(row -> decorate(row, actor));
        return PageResult.from(page);
    }

    /** 查询当前范围内可办理合同登记的成交商机。 */
    @Transactional(readOnly = true)
    public PageResult<ContractRegistrationVO> registrationOpportunities(
        AuthPrincipal actor,
        ContractQuery query
    ) {
        requireUsable(actor);
        var page = contracts.registrationOpportunities(
            new Page<>(query.getPage(), query.getSize()),
            DataScope.of(actor),
            query
        );
        page
            .getRecords()
            .forEach(row ->
                row.setCanUpload(
                    ("ADMIN".equals(actor.roleCode()) ||
                            Objects.equals(actor.id(), row.getOwnerId())) &&
                        (row.getContractId() == null ||
                            (EDITABLE.contains(row.getContractStatus()) &&
                                !PAUSED.contains(row.getSigningProgress())))
                )
            );
        return PageResult.from(page);
    }

    /** 查询详情并检查当前用户的数据权限。 */
    @Transactional(readOnly = true)
    public ContractDetailVO detail(AuthPrincipal actor, Long id) {
        ContractSummaryVO summary = visible(actor, id);
        ContractDetailVO detail = new ContractDetailVO();
        BeanUtils.copyProperties(summary, detail);
        Contract entity = contracts.find(id);
        if (entity != null) BeanUtils.copyProperties(entity, detail);
        detail.setAttachments(contracts.attachments(id));
        detail
            .getAttachments()
            .forEach(item ->
                item.setDownloadUrl(
                    "/api/contracts/" + id + "/attachments/" + item.getId() + "/download"
                )
            );
        detail.setOperationHistory(contracts.operationHistory(id));
        detail.setSigningHistory(contracts.signingHistory(id));
        return detail;
    }

    /** 锁定成交商机并创建唯一的有效合同登记草稿。 */
    @Transactional
    public Long createDraft(AuthPrincipal actor, ContractRequests.Create request) {
        requireUsable(actor);
        OpportunitySummaryVO opportunity = opportunities.summary(
            request.opportunityId(),
            DataScope.of(actor)
        );
        if (opportunity == null) throw new BizException(ErrorCode.NOT_FOUND);
        boolean manageable =
            "ADMIN".equals(actor.roleCode()) ||
            Objects.equals(actor.id(), opportunity.getOwnerId());
        if (!manageable) throw new BizException(ErrorCode.NOT_FOUND);
        // 与阶段推进遵循客户→商机锁序，避免重复点击或并发推进后创建过期草稿。
        if (customers.lock(opportunity.getCustomerId()) == null) throw new BizException(
            ErrorCode.NOT_FOUND
        );
        Opportunity locked = opportunities.lock(opportunity.getId());
        if (
            locked == null ||
            !Objects.equals(locked.getOwnerId(), opportunity.getOwnerId()) ||
            !Objects.equals(locked.getTeamId(), opportunity.getTeamId())
        ) throw new BizException(ErrorCode.CONFLICT);
        if (!"DEAL".equals(locked.getStageCode())) throw new BizException(ErrorCode.CONTRACT_RULE);
        if (contracts.currentByOpportunity(request.opportunityId()) != null) throw new BizException(
            ErrorCode.CONFLICT
        );
        LocalDateTime now = now();
        Contract contract = new Contract();
        contract.setContractNo(
            "CRM-" +
                now.toLocalDate().toString().replace("-", "") +
                "-" +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        );
        contract.setCustomerId(locked.getCustomerId());
        contract.setOpportunityId(locked.getId());
        contract.setOwnerId(locked.getOwnerId());
        contract.setTeamId(locked.getTeamId());
        contract.setContractName(locked.getOpportunityName() + "合同登记");
        contract.setCounterpartyName(opportunity.getCustomerName());
        contract.setContractAmount(scale(locked.getExpectedAmount()));
        contract.setOutstandingAmount(scale(locked.getExpectedAmount()));
        contract.setCreatedBy(actor.id());
        contract.setCreatedAt(now);
        contract.setUpdatedAt(now);
        if (contracts.insert(contract) != 1) throw new BizException(ErrorCode.CONFLICT);
        contracts.insertSigningHistory(
            contract.getId(),
            null,
            "DRAFT_GENERATED",
            actor.id(),
            actor.realName(),
            actor.roleCode(),
            actor.teamId(),
            now,
            "创建在线合同登记草稿，待填写并提交核对",
            false
        );
        audit(
            actor,
            contract.getId(),
            "CONTRACT_DRAFT_CREATED",
            null,
            snapshot(contract),
            "从成交商机创建合同登记草稿"
        );
        return contract.getId();
    }

    /** 校验可编辑状态后更新合同登记信息。 */
    @Transactional
    public void editDraft(AuthPrincipal actor, Long id, ContractRequests.Edit request) {
        requireUsable(actor);
        Contract contract = writable(actor, id, request.version(), false);
        if (!EDITABLE.contains(contract.getContractStatus())) throw new BizException(
            ErrorCode.CONTRACT_RULE
        );
        String before = snapshot(contract);
        String registrationBefore = registrationSnapshot(contract);
        contract.setContractName(request.contractName().trim());
        contract.setExternalContractNo(trim(request.externalContractNo()));
        contract.setBusinessType(trim(request.businessType()));
        contract.setOurPartyName(trim(request.ourPartyName()));
        contract.setCounterpartyName(trim(request.counterpartyName()));
        contract.setDescription(trim(request.description()));
        contract.setOurContactName(trim(request.ourContactName()));
        contract.setOurContactPhone(trim(request.ourContactPhone()));
        contract.setCounterpartyContactName(trim(request.counterpartyContactName()));
        contract.setCounterpartyContactPhone(trim(request.counterpartyContactPhone()));
        contract.setOurAddress(trim(request.ourAddress()));
        contract.setCounterpartyAddress(trim(request.counterpartyAddress()));
        contract.setOurCreditCode(trim(request.ourCreditCode()));
        contract.setCounterpartyCreditCode(trim(request.counterpartyCreditCode()));
        contract.setPaymentTerms(trim(request.paymentTerms()));
        contract.setDeliveryTerms(trim(request.deliveryTerms()));
        contract.setPlannedSignedAt(request.plannedSignedAt());
        contract.setPerformanceStartDate(request.performanceStartDate());
        contract.setContractAmount(scale(request.contractAmount()));
        contract.setSignedAt(request.signedAt());
        contract.setExpireDate(request.expireDate());
        if (
            contract.getExpireDate() != null &&
            contract.getSignedAt() != null &&
            contract.getExpireDate().isBefore(contract.getSignedAt())
        ) throw new BizException(ErrorCode.VALIDATION_ERROR);
        if (
            contract.getPerformanceStartDate() != null &&
            contract.getExpireDate() != null &&
            contract.getPerformanceStartDate().isAfter(contract.getExpireDate())
        ) throw new BizException(ErrorCode.VALIDATION_ERROR);
        if (contracts.updateDraft(contract, now()) != 1) throw new BizException(ErrorCode.CONFLICT);
        if (
            "SIGNED_UPLOAD".equals(contract.getSigningProgress()) &&
            !registrationBefore.equals(registrationSnapshot(contract))
        ) {
            Contract changed = contracts.find(id);
            registrationApproval(changed, null, null);
            move(
                actor,
                changed,
                "DRAFT_GENERATED",
                "DRAFT",
                null,
                "已核对的登记信息发生修改，需重新提交核对；历史文件保留",
                false
            );
        }
        audit(
            actor,
            id,
            "CONTRACT_DRAFT_UPDATED",
            before,
            snapshot(contracts.find(id)),
            "修改合同登记信息"
        );
    }

    /** 在线登记填妥后提交核对，不生成或上传草稿文件。 */
    @Transactional
    public void submitDraft(AuthPrincipal actor, Long id, ContractRequests.Submit request) {
        requireUsable(actor);
        Contract c = writable(actor, id, request.version(), false);
        if (!actor.id().equals(c.getOwnerId()) && !"ADMIN".equals(actor.roleCode())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!"DRAFT_GENERATED".equals(c.getSigningProgress())) throw new BizException(
            ErrorCode.CONTRACT_RULE
        );
        requireRegistrationComplete(c);
        registrationApproval(c, null, null);
        move(
            actor,
            c,
            "DRAFT_REVIEW",
            "PENDING_REVIEW",
            null,
            "在线合同登记表已提交，等待经理或管理员核对",
            false
        );
    }

    /** 负责人撤回待审登记，回到可编辑草稿；区别于暂停整个合同的已撤回状态。 */
    @Transactional
    public void withdrawDraft(
        AuthPrincipal actor,
        Long id,
        ContractRequests.WithdrawDraft request
    ) {
        requireUsable(actor);
        visible(actor, id);
        Contract c = contracts.lock(id);
        if (c == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (!actor.id().equals(c.getOwnerId())) throw new BizException(ErrorCode.FORBIDDEN);
        if (!Objects.equals(c.getVersion(), request.version())) throw new BizException(
            ErrorCode.CONFLICT
        );
        if (
            !"DRAFT_REVIEW".equals(c.getSigningProgress()) ||
            !"PENDING_REVIEW".equals(c.getContractStatus())
        ) {
            throw new BizException(ErrorCode.CONTRACT_RULE);
        }
        registrationApproval(c, null, null);
        move(
            actor,
            c,
            "DRAFT_GENERATED",
            "DRAFT",
            null,
            "负责人撤回登记核对：" + request.reason().trim(),
            false
        );
    }

    /** 检查资料类型和状态，保存文件及附件版本记录。 */
    @Transactional
    public void upload(
        AuthPrincipal actor,
        Long id,
        String materialType,
        String note,
        MultipartFile file
    ) {
        requireUsable(actor);
        Contract contract = writable(actor, id, null, false);
        if (!EDITABLE.contains(contract.getContractStatus())) throw new BizException(
            ErrorCode.CONTRACT_RULE
        );
        if (
            file == null || file.isEmpty() || file.getSize() > 20 * 1024 * 1024L
        ) throw new BizException(ErrorCode.CONTRACT_FILE_INVALID);
        String type = trim(materialType);
        if (!Set.of("SIGNED_CONTRACT", "SUPPLEMENT").contains(type)) throw new BizException(
            ErrorCode.CONTRACT_FILE_INVALID
        );
        if (
            ("SIGNED_CONTRACT".equals(type) &&
                !"SIGNED_UPLOAD".equals(contract.getSigningProgress()))
        ) {
            throw new BizException(ErrorCode.CONTRACT_RULE);
        }
        String original = safeFileName(file.getOriginalFilename());
        String lower = original.toLowerCase(Locale.ROOT);
        String extension = ALLOWED_EXTENSIONS.stream()
            .filter(lower::endsWith)
            .findFirst()
            .orElse("");
        if (
            extension.isEmpty() ||
            (file.getContentType() != null && !ALLOWED_TYPES.contains(file.getContentType()))
        ) throw new BizException(ErrorCode.CONTRACT_FILE_INVALID);
        try (var content = file.getInputStream()) {
            String stored = fileStorage.store(id, extension, content, file.getSize());
            // 本地文件与数据库不是同一事务；元数据或审计回滚只清理本次新增文件。
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    /** 保留历史版本，回滚补偿仅针对本次上传。 */
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) fileStorage.deleteCreated(stored, id);
                    }
                }
            );
            ContractAttachment attachment = new ContractAttachment();
            LocalDateTime now = now();
            attachment.setBizId(id);
            attachment.setMaterialType(type);
            attachment.setVersionNo(contracts.nextAttachmentVersion(id, type));
            attachment.setFileName(original);
            attachment.setStoragePath(stored);
            attachment.setFileSize(file.getSize());
            attachment.setUploadedBy(actor.id());
            attachment.setUploadedAt(now);
            attachment.setCredentialNote(trim(note));
            if (contracts.insertAttachment(attachment) != 1) throw new BizException(
                ErrorCode.CONFLICT
            );
            // 每次上传都递增版本；核验只读取该环节的最新文件。
            if (
                contracts.transition(
                    contract,
                    contract.getSigningProgress(),
                    contract.getContractStatus(),
                    null,
                    now
                ) !=
                1
            ) {
                throw new BizException(ErrorCode.CONFLICT);
            }
            audit(
                actor,
                id,
                "CONTRACT_FILE_UPLOADED",
                null,
                "{\"attachmentId\":" +
                    attachment.getId() +
                    ",\"versionNo\":" +
                    attachment.getVersionNo() +
                    "}",
                "上传合同资料版本"
            );
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
        }
    }

    /** 仅负责人或管理员明确提交最新实际签署文件，推进到待确认。 */
    @Transactional
    public void submit(AuthPrincipal actor, Long id, ContractRequests.Submit request) {
        requireUsable(actor);
        Contract c = writable(actor, id, request.version(), false);
        if (
            !actor.id().equals(c.getOwnerId()) && !"ADMIN".equals(actor.roleCode())
        ) throw new BizException(ErrorCode.FORBIDDEN);
        if (!"SIGNED_UPLOAD".equals(c.getSigningProgress())) throw new BizException(
            ErrorCode.CONTRACT_RULE
        );
        requireRegistrationApproved(c);
        requireMaterial(c, "SIGNED_CONTRACT", "PENDING");
        requireCompleteDetails(c);
        move(
            actor,
            c,
            "FILE_REVIEW",
            "PENDING_REVIEW",
            null,
            "负责人确认实际签署文件，提交内部核验",
            false
        );
    }

    /** 三角色可标记异常或撤回；经理/管理员恢复保存的节点并重新校验材料。 */
    @Transactional
    public void updateSigningProgress(
        AuthPrincipal actor,
        Long id,
        ContractRequests.SigningProgress request
    ) {
        requireUsable(actor);
        ContractSummaryVO row = visible(actor, id);
        if (
            !Boolean.TRUE.equals(row.getCanUpdateSigningProgress()) &&
            !Boolean.TRUE.equals(row.getCanCorrectSigningProgress())
        ) throw new BizException(ErrorCode.FORBIDDEN);
        customers.lock(row.getCustomerId());
        Contract c = contracts.lock(id);
        if (c == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (!Objects.equals(request.version(), c.getVersion())) throw new BizException(
            ErrorCode.CONFLICT
        );
        String to = request.targetProgress();
        boolean management =
            "ADMIN".equals(actor.roleCode()) ||
            ("MANAGER".equals(actor.roleCode()) && Objects.equals(actor.teamId(), c.getTeamId()));
        if ("NORMAL".equals(to)) {
            if (!management) throw new BizException(ErrorCode.FORBIDDEN);
            if (
                !PAUSED.contains(c.getSigningProgress()) ||
                !NORMAL_PROGRESS.contains(c.getResumeSigningProgress())
            ) throw new BizException(ErrorCode.CONTRACT_RULE);
            to = c.getResumeSigningProgress();
            validateResume(c, to);
            // 老版本的 VOID 已撤回只在没有替代登记时恢复，避免同一商机两份有效登记。
            if (
                "VOID".equals(c.getContractStatus()) &&
                contracts.currentByOpportunity(c.getOpportunityId()) != null
            ) throw new BizException(ErrorCode.CONFLICT);
            move(
                actor,
                c,
                to,
                "VOID".equals(c.getContractStatus()) ? "DRAFT" : c.getContractStatus(),
                null,
                request.reason().trim(),
                true
            );
            Opportunity opportunity = opportunities.find(c.getOpportunityId());
            if (
                "FILE_CONFIRMED".equals(to) &&
                opportunity != null &&
                "DEAL".equals(opportunity.getStageCode())
            ) {
                opportunityService.advanceFromBusiness(opportunity.getId());
            }
        } else {
            if (!PAUSED.contains(to) || to.equals(c.getSigningProgress())) throw new BizException(
                ErrorCode.CONTRACT_RULE
            );
            String resume = PAUSED.contains(c.getSigningProgress())
                ? c.getResumeSigningProgress()
                : c.getSigningProgress();
            if (!NORMAL_PROGRESS.contains(resume)) throw new BizException(ErrorCode.CONTRACT_RULE);
            move(actor, c, to, c.getContractStatus(), resume, request.reason().trim(), false);
        }
    }

    /** 在两个核验环节按具体最新文件版本确认/退回，最终确认才联动商机签约。 */
    @Transactional
    public void review(AuthPrincipal actor, Long id, ContractRequests.Review request) {
        requireUsable(actor);
        if (!Set.of("ADMIN", "MANAGER").contains(actor.roleCode())) throw new BizException(
            ErrorCode.FORBIDDEN
        );
        Contract initial = contracts.find(id);
        if (initial == null) throw new BizException(ErrorCode.NOT_FOUND);
        customers.lock(initial.getCustomerId());
        Contract c = writable(actor, id, request.version(), true);
        if (
            !"PENDING_REVIEW".equals(c.getContractStatus()) ||
            Objects.equals(actor.id(), c.getOwnerId())
        ) throw new BizException(ErrorCode.FORBIDDEN);
        boolean draft = "DRAFT_REVIEW".equals(c.getSigningProgress());
        if (!draft && !"FILE_REVIEW".equals(c.getSigningProgress())) throw new BizException(
            ErrorCode.CONTRACT_RULE
        );
        if (draft) {
            if (request.pass()) requireRegistrationComplete(c);
        } else {
            ContractAttachment file = requireMaterial(c, "SIGNED_CONTRACT", "PENDING");
            if (
                contracts.confirmAttachment(
                    file.getId(),
                    request.pass() ? "PASSED" : "REJECTED",
                    request.opinion().trim(),
                    actor.id(),
                    now()
                ) !=
                1
            ) throw new BizException(ErrorCode.CONFLICT);
        }
        if (!request.pass()) {
            move(
                actor,
                c,
                draft ? "DRAFT_GENERATED" : "SIGNED_UPLOAD",
                "RETURNED",
                null,
                "退回补充：" + request.opinion().trim(),
                false
            );
            return;
        }
        if (draft) {
            registrationApproval(c, now(), actor.id());
            move(
                actor,
                c,
                "SIGNED_UPLOAD",
                "DRAFT",
                null,
                "合同登记表核对通过：" + request.opinion().trim(),
                false
            );
            return;
        }
        requireCompleteDetails(c);
        Opportunity opportunity = opportunities.lock(c.getOpportunityId());
        if (
            opportunity == null || !"DEAL".equals(opportunity.getStageCode())
        ) throw new BizException(ErrorCode.CONTRACT_RULE);
        if (
            opportunities.markSignedConfirmed(
                opportunity.getId(),
                opportunity.getVersion(),
                now()
            ) !=
            1
        ) throw new BizException(ErrorCode.CONFLICT);
        move(
            actor,
            c,
            "FILE_CONFIRMED",
            "SIGNED",
            null,
            "实际签署合同文件核验通过：" + request.opinion().trim(),
            false
        );
        contracts.capturePerformance(c.getId(), now());
        contracts.protectCustomer(c.getCustomerId(), now());
        opportunityService.advanceFromBusiness(opportunity.getId());
        paymentService.refreshAfterSigning(c.getId());
    }

    /** 更新状态、进度历史和审计同一事务，拒绝旧版本与重复请求。 */
    private void move(
        AuthPrincipal actor,
        Contract c,
        String to,
        String status,
        String resume,
        String reason,
        boolean correction
    ) {
        LocalDateTime now = now();
        if (contracts.transition(c, to, status, resume, now) != 1) throw new BizException(
            ErrorCode.CONFLICT
        );
        contracts.insertSigningHistory(
            c.getId(),
            c.getSigningProgress(),
            to,
            actor.id(),
            actor.realName(),
            actor.roleCode(),
            actor.teamId(),
            now,
            reason,
            correction
        );
        audit(
            actor,
            c.getId(),
            "CONTRACT_SIGNING_PROGRESS_CHANGED",
            snapshot(c),
            snapshot(contracts.find(c.getId())),
            reason
        );
    }

    /** 文件元数据和实际文件同时存在，且最新版本满足指定确认状态。 */
    private ContractAttachment requireMaterial(Contract c, String type, String status) {
        ContractAttachment file = contracts.latestMaterial(c.getId(), type);
        if (file == null || !status.equals(file.getConfirmStatus())) throw new BizException(
            ErrorCode.CONTRACT_REVIEW_REQUIRED
        );
        if (!fileStorage.exists(file.getStoragePath(), c.getId())) throw new BizException(
            ErrorCode.CONTRACT_REVIEW_REQUIRED
        );
        return file;
    }

    /** 恢复暂停步骤时重查对应材料，已确认合同保留确认结果。 */
    private void validateResume(Contract c, String step) {
        switch (step) {
            case "DRAFT_REVIEW" -> requireRegistrationComplete(c);
            case "SIGNED_UPLOAD" -> requireRegistrationApproved(c);
            case "FILE_REVIEW" -> {
                requireMaterial(c, "SIGNED_CONTRACT", "PENDING");
                requireCompleteDetails(c);
            }
            case "FILE_CONFIRMED" -> {
                requireMaterial(c, "SIGNED_CONTRACT", "PASSED");
                requireCompleteDetails(c);
            }
            default -> {}
        }
    }

    /** 校验附件归属和数据权限后返回文件资源。 */
    public Resource download(AuthPrincipal actor, Long id, Long attachmentId) {
        requireUsable(actor);
        visible(actor, id);
        ContractAttachment attachment = contracts.attachment(attachmentId, id);
        if (attachment == null) throw new BizException(ErrorCode.NOT_FOUND);
        return fileStorage.download(attachment.getStoragePath(), id, attachment.getFileName(), attachment.getFileSize());
    }

    /** 查询记录并校验当前用户的可见范围。 */
    private ContractSummaryVO visible(AuthPrincipal actor, Long id) {
        ContractSummaryVO row = contracts.summary(id, DataScope.of(actor));
        if (row == null) throw new BizException(ErrorCode.NOT_FOUND);
        decorate(row, actor);
        return row;
    }

    /** 锁定记录并校验写入权限及版本。 */
    private Contract writable(AuthPrincipal actor, Long id, Long version, boolean management) {
        ContractSummaryVO row = visible(actor, id);
        if (
            !Boolean.TRUE.equals(row.getCanEdit()) &&
            !(management && Boolean.TRUE.equals(row.getCanReview()))
        ) throw new BizException(ErrorCode.FORBIDDEN);
        Contract c = contracts.lock(id);
        if (c == null) throw new BizException(ErrorCode.NOT_FOUND);
        // 获锁后重验最新状态与归属，防止读详情后被其他操作暂停或改归属。
        ContractSummaryVO locked = new ContractSummaryVO();
        BeanUtils.copyProperties(c, locked);
        decorate(locked, actor);
        if (
            !Boolean.TRUE.equals(locked.getCanEdit()) &&
            !(management && Boolean.TRUE.equals(locked.getCanReview()))
        ) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (version != null && !Objects.equals(version, c.getVersion())) throw new BizException(
            ErrorCode.CONFLICT
        );
        return c;
    }

    /** 后端返回合法动作及当前材料说明；页面不能任意选择正常节点。 */
    private void decorate(ContractSummaryVO row, AuthPrincipal actor) {
        boolean admin = "ADMIN".equals(actor.roleCode());
        boolean manager =
            admin ||
            ("MANAGER".equals(actor.roleCode()) && Objects.equals(actor.teamId(), row.getTeamId()));
        boolean owner = Objects.equals(actor.id(), row.getOwnerId());
        boolean scope = owner || manager;
        String step = row.getSigningProgress();
        boolean paused = PAUSED.contains(step);
        boolean edit = (owner || admin) && EDITABLE.contains(row.getContractStatus()) && !paused;
        row.setCanEdit(edit);
        row.setCanSubmitDraft(edit && "DRAFT_GENERATED".equals(step));
        row.setCanWithdrawDraft(
            owner && "DRAFT_REVIEW".equals(step) && "PENDING_REVIEW".equals(row.getContractStatus())
        );
        row.setCanSubmit(edit && "SIGNED_UPLOAD".equals(step) && (owner || admin));
        row.setCanReview(
            manager &&
                !owner &&
                !paused &&
                "PENDING_REVIEW".equals(row.getContractStatus()) &&
                Set.of("DRAFT_REVIEW", "FILE_REVIEW").contains(step)
        );
        row.setCanUpdateSigningProgress(
            scope && (!"VOID".equals(row.getContractStatus()) || paused)
        );
        row.setCanCorrectSigningProgress(manager && paused);
        row.setUploadMaterialType(edit && "SIGNED_UPLOAD".equals(step) ? "SIGNED_CONTRACT" : null);
        row.setProgressInstruction(
            switch (step) {
                case "DRAFT_GENERATED" -> "在线填写合同登记表，可反复保存修改；填妥后提交经理或管理员核对，不需要上传草稿文件。";
                case "DRAFT_REVIEW" -> "登记表等待经理或管理员核对；负责人可撤回修改。核对通过进入合同签署，打回后修改并重新提交。";
                case "SIGNED_UPLOAD" -> "上传实际签署文件并填写双方主体、金额、实际签订日期；由负责人或管理员点击确定提交。";
                case "FILE_REVIEW" -> "等待本团队经理或管理员核验最新实际签署文件；退回后须补传新版本。";
                case "FILE_CONFIRMED" -> "合同文件已确认；商机还需满足成交阶段已核验有效跟进门槛才能自动推进赢单。";
                default -> "当前进度已暂停；经理或管理员可恢复到暂停前步骤，恢复时重新核对材料。";
            }
        );
    }

    /** 写入当前业务操作的审计记录。 */
    private void audit(
        AuthPrincipal actor,
        Long id,
        String action,
        String before,
        String after,
        String reason
    ) {
        contracts.audit(id, action, actor.id(), actor.teamId(), now(), before, after, reason);
    }

    /** 拒绝尚未完成首次改密的用户操作。 */
    private void requireUsable(AuthPrincipal actor) {
        if (actor == null || actor.mustChangePassword()) throw new BizException(
            ErrorCode.PASSWORD_CHANGE_REQUIRED
        );
    }

    /** 只比较登记信息，后补实际签订日期不撤销已完成的登记核对。 */
    private String registrationSnapshot(Contract c) {
        var tree = json.valueToTree(c);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree).remove(
            java.util.List.of(
                "signedAt",
                "version",
                "updatedAt",
                "registrationApprovedAt",
                "registrationApprovedBy"
            )
        );
        return tree.toString();
    }

    /** 在线登记送审要求业务类型、双方公司、双方联系人电话和业务内容完整。 */
    private void requireRegistrationComplete(Contract c) {
        if (
            trim(c.getContractName()) == null ||
            trim(c.getBusinessType()) == null ||
            trim(c.getOurPartyName()) == null ||
            trim(c.getCounterpartyName()) == null ||
            trim(c.getOurContactName()) == null ||
            trim(c.getOurContactPhone()) == null ||
            trim(c.getCounterpartyContactName()) == null ||
            trim(c.getCounterpartyContactPhone()) == null ||
            trim(c.getDescription()) == null ||
            c.getContractAmount() == null
        ) {
            throw new BizException(ErrorCode.CONTRACT_DETAILS_REQUIRED);
        }
    }

    /** 核对批准信息与状态流转在同一合同锁和事务内写入。 */
    private void registrationApproval(Contract c, LocalDateTime at, Long reviewer) {
        if (contracts.registrationApproval(c, at, reviewer) != 1) throw new BizException(
            ErrorCode.CONFLICT
        );
    }

    /** 实际签署文件送审前必须完成在线登记核对。 */
    private void requireRegistrationApproved(Contract c) {
        if (c.getRegistrationApprovedAt() == null) throw new BizException(
            ErrorCode.CONTRACT_REVIEW_REQUIRED
        );
    }

    /** 检查合同主体、金额和业务字段是否完整。 */
    private void requireCompleteDetails(Contract c) {
        if (
            trim(c.getContractNo()) == null ||
            trim(c.getOurPartyName()) == null ||
            trim(c.getCounterpartyName()) == null ||
            c.getContractAmount() == null ||
            c.getSignedAt() == null
        ) throw new BizException(ErrorCode.CONTRACT_DETAILS_REQUIRED);
    }

    /** 从注入的业务时钟取得当前时间。 */
    private LocalDateTime now() {
        return LocalDateTime.now(businessClock);
    }

    /** 去除文本首尾空白。 */
    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 规范合同金额的小数精度。 */
    private BigDecimal scale(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.scale() > 2) throw new BizException(
            ErrorCode.VALIDATION_ERROR
        );
        return value.setScale(2);
    }

    /** 清理附件下载文件名中的不安全字符。 */
    private String safeFileName(String name) {
        String value = name == null ? "contract-file" : name.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        return value.isBlank()
            ? "contract-file"
            : value.length() > 255
                ? value.substring(0, 255)
                : value;
    }

    /** 将业务对象序列化为审计快照。 */
    private String snapshot(Contract c) {
        try {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", c.getId());
            value.put("contractNo", c.getContractNo());
            value.put("status", c.getContractStatus());
            value.put("signingProgress", c.getSigningProgress());
            value.put("resumeSigningProgress", c.getResumeSigningProgress());
            value.put("amount", c.getContractAmount());
            value.put("version", c.getVersion());
            value.put("externalContractNo",c.getExternalContractNo());
            value.put("contractName",c.getContractName());
            value.put("businessType",c.getBusinessType());
            value.put("ourPartyName",c.getOurPartyName());
            value.put("counterpartyName",c.getCounterpartyName());
            value.put("plannedSignedAt",c.getPlannedSignedAt());
            value.put("signedAt",c.getSignedAt());
            value.put("expireDate",c.getExpireDate());
            value.put("receivedAmount",c.getReceivedAmount());
            value.put("outstandingAmount",c.getOutstandingAmount());
            value.put("ourContactName",c.getOurContactName());
            value.put("counterpartyContactName",c.getCounterpartyContactName());
            value.put("ourContactPhone",auditText(c.getOurContactPhone()));
            value.put("counterpartyContactPhone",auditText(c.getCounterpartyContactPhone()));
            value.put("description",auditText(c.getDescription()));
            value.put("paymentTerms",auditText(c.getPaymentTerms()));
            value.put("deliveryTerms",auditText(c.getDeliveryTerms()));
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("无法记录合同审计", e);
        }
    }

    /** 审计正文遮蔽电话、邮箱和信用代码；不复制合同文件内容。 */
    private String auditText(String value) {
        return value==null?null:value.replaceAll("[0-9A-Z]{18}","[信用代码已隐藏]")
            .replaceAll("\\+?[0-9][0-9 ()-]{5,}[0-9]","[号码已隐藏]")
            .replaceAll("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}","[邮箱已隐藏]");
    }
}
