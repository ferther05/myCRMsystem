package com.bishe.crm.module.opportunity.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.common.api.PageResult;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.auth.security.AuthUsers;
import com.bishe.crm.module.auth.security.DataScope;
import com.bishe.crm.module.opportunity.dto.OpportunityQuery;
import com.bishe.crm.module.opportunity.dto.OpportunityRequests;
import com.bishe.crm.module.opportunity.entity.Opportunity;
import com.bishe.crm.module.opportunity.entity.OpportunityCollaborator;
import com.bishe.crm.module.opportunity.entity.OpportunityStageLog;
import com.bishe.crm.module.opportunity.mapper.OpportunityMapper;
import com.bishe.crm.module.opportunity.vo.*;
import com.bishe.crm.module.system.entity.SystemUser;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import com.bishe.crm.module.system.mapper.AccountMapper;
import com.bishe.crm.module.customer.mapper.CustomerMapper;
import com.bishe.crm.module.customer.mapper.FollowRecordMapper;
import com.bishe.crm.module.customer.entity.Customer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 商机 9-14 的业务编排：状态机、范围、协作、金额审计和漏斗。 */
@Service
@RequiredArgsConstructor
public class OpportunityService {
    private static final Map<String, Integer> DEFAULT_DAYS = Map.of("NEW", 20, "FOLLOWING", 30, "QUOTED", 45, "DEAL", 45);
    private static final List<String> ACTIVE_STAGES = List.of("NEW", "FOLLOWING", "QUOTED", "DEAL");
    private static final List<String> STAGE_ORDER = List.of("NEW", "FOLLOWING", "QUOTED", "DEAL", "WON");
    private static final Map<String, String> STAGE_NAMES = Map.of("NEW", "线索", "FOLLOWING", "跟进", "QUOTED", "报价", "DEAL", "成交", "WON", "赢单", "LOST", "输单", "CLOSED", "关闭");
    private final OpportunityMapper opportunities;
    private final SystemUserMapper users;
    private final AccountMapper accounts;
    private final ObjectMapper json;
    private final Clock businessClock;
    private final CustomerMapper customers;
    private final FollowRecordMapper follows;

    @Transactional(readOnly = true)
    public PageResult<OpportunitySummaryVO> list(AuthPrincipal actor, OpportunityQuery query) {
        IPage<OpportunitySummaryVO> page = opportunities.list(query.toPage(), DataScope.of(actor), query);
        page.getRecords().forEach(row -> decorate(actor, row));
        return PageResult.from(page);
    }

    @Transactional(readOnly = true)
    public OpportunityDetailVO detail(AuthPrincipal actor, Long id) {
        OpportunitySummaryVO summary = visible(actor, id);
        OpportunityDetailVO result = new OpportunityDetailVO();
        BeanUtils.copyProperties(summary, result);
        result.setCollaborators(opportunities.collaborators(id));
        result.setStageHistory(opportunities.stageHistory(id));
        Opportunity entity = opportunities.find(id);
        result.setStageProgress(stageProgress(entity));
        result.setQuoteAmount(entity.getQuoteAmount()); result.setQuoteAt(entity.getQuoteAt());
        result.setCustomerConfirmResult(entity.getCustomerConfirmResult()); result.setCustomerConfirmAt(entity.getCustomerConfirmAt());
        Customer customer = customers.selectById(entity.getCustomerId());
        result.setCustomerVersion(customer.getVersion());
        result.setContacts(opportunities.minimalContacts(entity.getCustomerId()));
        result.setFollowRecords(follows.byOpportunity(id));
        result.setOperationHistory(Boolean.TRUE.equals(summary.getCanEdit()) ? opportunities.operationHistory(id) : List.of());
        return result;
    }

    @Transactional
    public Long create(AuthPrincipal actor, OpportunityRequests.Create request) {
        if (!"ADMIN".equals(actor.roleCode())) throw new BizException(ErrorCode.FORBIDDEN);
        requireUsable(actor);
        Customer customer = customers.lock(request.customerId());
        if (customer == null || opportunities.writableCustomer(request.customerId(), DataScope.of(actor)) != 1) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!List.of("FOLLOWING", "PROTECTED").contains(customer.getCustomerStatus())) throw new BizException(ErrorCode.OPPORTUNITY_RULE);
        SystemUser owner = accounts.lockUser(customer.getOwnerId());
        if (!AuthUsers.usable(owner) || !List.of("MANAGER", "SPECIALIST").contains(owner.getRoleCode())
                || !Objects.equals(owner.getTeamId(), customer.getTeamId())) {
            throw new BizException(ErrorCode.ACCOUNT_RULE);
        }
        if (request.ownerId() != null && !request.ownerId().equals(owner.getId())) {
            throw new BizException(ErrorCode.CONFLICT);
        }
        Opportunity opportunity = new Opportunity();
        LocalDateTime now = now();
        opportunity.setCustomerId(request.customerId());
        opportunity.setOpportunityName(request.opportunityName().trim());
        opportunity.setOwnerId(owner.getId());
        opportunity.setTeamId(owner.getTeamId());
        opportunity.setStageCode("NEW");
        opportunity.setStagnationDays(DEFAULT_DAYS.get("NEW"));
        opportunity.setGraceDays(10);
        opportunity.setThresholdCustomized(false);
        opportunity.setActivityStatus("ACTIVE");
        opportunity.setExpectedAmount(money(request.expectedAmount()));
        opportunity.setExpectedCloseDate(request.expectedCloseDate());
        opportunity.setLastEffectiveProgressAt(now);
        opportunity.setStageChangedAt(now);
        opportunity.setCreatedBy(actor.id());
        opportunity.setCreatedAt(now);
        opportunity.setUpdatedAt(now);
        if (opportunities.insertOpportunity(opportunity) != 1) throw new BizException(ErrorCode.CONFLICT);
        OpportunityStageLog log = stageLog(opportunity.getId(), null, "NEW", "CREATE", "创建商机", actor, now);
        opportunities.insertStageLog(log);
        audit(actor, opportunity.getId(), "OPPORTUNITY_CREATED", null, snapshot(opportunity), "创建商机");
        opportunities.refreshCustomerProtection(customer.getId(), now);
        return opportunity.getId();
    }

    @Transactional
    public void edit(AuthPrincipal actor, Long id, OpportunityRequests.Edit request) {
        requireUsable(actor);
        Opportunity opportunity = writable(actor, id, request.version(), false);
        requireActive(opportunity);
        String before = snapshot(opportunity);
        opportunity.setOpportunityName(request.opportunityName().trim());
        opportunity.setExpectedAmount(money(request.expectedAmount()));
        opportunity.setExpectedCloseDate(request.expectedCloseDate());
        int changed = opportunities.updateBasic(opportunity, now());
        if (changed != 1) throw new BizException(ErrorCode.CONFLICT);
        Opportunity after = opportunities.lock(id);
        audit(actor, id, "OPPORTUNITY_BASIC_UPDATED", before, snapshot(after), "修改商机金额或预计成交日期");
    }

    @Transactional
    public void changeStage(AuthPrincipal actor, Long id, OpportunityRequests.StageAction request) {
        requireUsable(actor);
        if (!"ADMIN".equals(actor.roleCode())) throw new BizException(ErrorCode.FORBIDDEN);
        Opportunity opportunity = writable(actor, id, request.version(), false);
        if (text(request.reason()) == null) throw new BizException(ErrorCode.VALIDATION_ERROR);
        transition(opportunity, request.targetStage(), request.reason(), actor, null);
    }

    /** 仅供业务事务调用；没有开放任意目标阶段的自动流转接口。 */
    @Transactional
    public void advanceFromBusiness(Long id) {
        Opportunity initial = opportunities.find(id);
        if (initial == null) throw new BizException(ErrorCode.NOT_FOUND);
        Customer customer = customers.lock(initial.getCustomerId());
        if (customer == null || !"PRIVATE".equals(customer.getSeaType())) throw new BizException(ErrorCode.OPPORTUNITY_RULE);
        Opportunity opportunity = opportunities.lock(id);
        while (ACTIVE_STAGES.contains(opportunity.getStageCode())) {
            String next = STAGE_ORDER.get(STAGE_ORDER.indexOf(opportunity.getStageCode()) + 1);
            if (!requirements(opportunity, next).stream().allMatch(OpportunityStageProgressVO.Requirement::met)) break;
            String action = switch (next) {
                case "FOLLOWING" -> "AUTO_FOLLOW";
                case "QUOTED" -> "AUTO_QUOTE";
                case "DEAL" -> "AUTO_CONFIRMATION";
                default -> "AUTO_CONTRACT_REVIEW";
            };
            transition(opportunity, next, "业务条件满足，系统自动推进至" + STAGE_NAMES.get(next), null, action);
            opportunity = opportunities.lock(id);
        }
    }

    private void transition(Opportunity opportunity, String to, String reason, AuthPrincipal actor, String automaticAction) {
        Long id = opportunity.getId();
        String from = opportunity.getStageCode();
        validateStage(from, to, reason, opportunity);
        if (STAGE_ORDER.indexOf(to) > STAGE_ORDER.indexOf(from)) {
            for (var requirement : requirements(opportunity, to)) {
                if (!requirement.met()) throw new BizException(switch (requirement.code()) {
                    case "QUOTE" -> ErrorCode.OPPORTUNITY_QUOTE_REQUIRED;
                    case "CUSTOMER_CONFIRMATION" -> ErrorCode.OPPORTUNITY_CONFIRM_REQUIRED;
                    case "SIGNED_MATERIAL", "SIGNING_REVIEW", "ACTIVE_CONTRACT" -> ErrorCode.OPPORTUNITY_SIGNING_REQUIRED;
                    case "CONTRACT_DETAILS" -> ErrorCode.CONTRACT_DETAILS_REQUIRED;
                    default -> ErrorCode.OPPORTUNITY_FOLLOW_REQUIRED;
                });
            }
        }
        String before = snapshot(opportunity);
        LocalDateTime now = now();
        String action;
        String activity;
        LocalDateTime closePending = opportunity.getClosePendingAt();
        LocalDateTime terminal = opportunity.getTerminalAt();
        if (List.of("WON", "LOST", "CLOSED").contains(to)) {
            action = "LOST".equals(to) ? "MARK_LOST" : "CLOSED".equals(to) ? "CLOSE" : "MARK_WON";
            activity = "TERMINATED";
            terminal = now;
            closePending = null;
        } else {
            action = STAGE_ORDER.indexOf(to) > STAGE_ORDER.indexOf(from) ? "ADVANCE" : "ROLLBACK";
            activity = "ACTIVE";
            terminal = null;
            closePending = null;
        }
        if (!Boolean.TRUE.equals(opportunity.getThresholdCustomized()) && !Objects.equals(from, to)) {
            int days = DEFAULT_DAYS.getOrDefault(to, opportunity.getStagnationDays());
            int grace = List.of("QUOTED", "DEAL").contains(to) ? 15 : 10;
            if (opportunities.updateDefaultThreshold(id, opportunity.getVersion(), days, grace, now) != 1) {
                throw new BizException(ErrorCode.CONFLICT);
            }
            opportunity.setVersion(opportunity.getVersion() + 1);
            opportunity.setStagnationDays(days);
            opportunity.setGraceDays(grace);
        }
        reason = text(reason);
        int changed = opportunities.updateStage(id, opportunity.getVersion(), to, activity, closePending, terminal,
                List.of("LOST", "CLOSED").contains(to) ? reason : null,
                "AUTO_FOLLOW".equals(automaticAction) ? opportunity.getLastEffectiveProgressAt() : now, now);
        if (changed != 1) throw new BizException(ErrorCode.CONFLICT);
        if (automaticAction != null) action = automaticAction;
        OpportunityStageLog log = stageLog(id, from, to, action, reason, actor, now);
        opportunities.insertStageLog(log);
        audit(actor, id, "OPPORTUNITY_STAGE_CHANGED", before, snapshot(opportunities.lock(id)), reason);
        opportunities.resolveTodos(id, now);
        if (List.of("WON", "LOST", "CLOSED").contains(to)) opportunities.endCollaborators(id, now);
        opportunities.refreshCustomerProtection(opportunity.getCustomerId(), now);
    }

    @Transactional
    public void updateThreshold(AuthPrincipal actor, Long id, OpportunityRequests.Threshold request) {
        requireUsable(actor);
        if (!List.of("ADMIN", "MANAGER").contains(actor.roleCode())) throw new BizException(ErrorCode.FORBIDDEN);
        Opportunity opportunity = writable(actor, id, request.version(), true);
        requireActive(opportunity);
        if (request.stagnationDays() < 1 || request.graceDays() < 0) throw new BizException(ErrorCode.VALIDATION_ERROR);
        String before = snapshot(opportunity);
        LocalDateTime now = now();
        if (opportunities.updateThreshold(id, opportunity.getVersion(), request.stagnationDays(), request.graceDays(), now) != 1) {
            throw new BizException(ErrorCode.CONFLICT);
        }
        Opportunity after = opportunities.lock(id);
        audit(actor, id, "OPPORTUNITY_THRESHOLD_UPDATED", before, snapshot(after), "自定义当前商机停滞阈值；仅影响该商机");
    }

    @Transactional
    public void addCollaborator(AuthPrincipal actor, Long id, OpportunityRequests.CollaboratorWrite request) {
        requireUsable(actor);
        Opportunity opportunity = writable(actor, id, request.version(), true);
        requireActive(opportunity);
        if (opportunities.activeCollaboratorCount(id) >= 3) throw new BizException(ErrorCode.OPPORTUNITY_COLLABORATION_RULE);
        if (opportunity.getOwnerId().equals(request.userId()) || opportunities.activeCollaborator(id, request.userId()) > 0) {
            throw new BizException(ErrorCode.CONFLICT);
        }
        SystemUser target = users.selectById(request.userId());
        if (target == null || !"SPECIALIST".equals(target.getRoleCode()) || !"ACTIVE".equals(target.getAccountStatus()) || !Objects.equals(target.getTeamId(), opportunity.getTeamId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        BigDecimal ratio = request.splitRatio().setScale(2);
        if (opportunities.collaboratorRatio(id).add(ratio).compareTo(BigDecimal.valueOf(30)) > 0) {
            throw new BizException(ErrorCode.OPPORTUNITY_COLLABORATION_RULE);
        }
        LocalDateTime now = now();
        OpportunityCollaborator collaborator = new OpportunityCollaborator();
        collaborator.setOpportunityId(id); collaborator.setUserId(request.userId()); collaborator.setSplitRatio(ratio);
        collaborator.setJoinedAt(now); collaborator.setCreatedAt(now); collaborator.setUpdatedAt(now); collaborator.setVersion(0L);
        if (opportunities.insertCollaborator(collaborator) != 1) throw new BizException(ErrorCode.CONFLICT);
        if (opportunities.bumpVersion(id,opportunity.getVersion(),now)!=1) throw new BizException(ErrorCode.CONFLICT);
        audit(actor, id, "OPPORTUNITY_COLLABORATOR_ADDED", null,
                "{\"userId\":" + request.userId() + ",\"splitRatio\":" + ratio + "}", "新增商机协作人");
    }

    @Transactional
    public void removeCollaborator(AuthPrincipal actor, Long id, OpportunityRequests.CollaboratorRemove request) {
        requireUsable(actor);
        Opportunity opportunity = writable(actor, id, request.version(), true);
        requireActive(opportunity);
        OpportunityCollaborator collaborator = opportunities.lockCollaborator(request.collaboratorId(), id);
        if (collaborator == null || collaborator.getUserId().equals(actor.id())) throw new BizException(ErrorCode.FORBIDDEN);
        LocalDateTime now = now();
        if (opportunities.removeCollaborator(collaborator.getId(), collaborator.getVersion(), now) != 1) throw new BizException(ErrorCode.CONFLICT);
        if (opportunities.bumpVersion(id,opportunity.getVersion(),now)!=1) throw new BizException(ErrorCode.CONFLICT);
        audit(actor, id, "OPPORTUNITY_COLLABORATOR_REMOVED", null,
                "{\"collaboratorId\":" + collaborator.getId() + "}", "移除商机协作人");
    }

    @Transactional(readOnly = true)
    public List<OpportunityFunnelVO> funnel(AuthPrincipal actor, LocalDateTime from, LocalDateTime to, Long ownerId) {
        if (from!=null && to!=null && !from.isBefore(to)) throw new BizException(ErrorCode.VALIDATION_ERROR);
        List<OpportunityFunnelVO> rows = opportunities.funnel(DataScope.of(actor), from, to, ownerId);
        Map<String, String> names = Map.of("NEW", "线索", "FOLLOWING", "跟进", "QUOTED", "报价", "DEAL", "成交", "WON", "赢单", "LOST", "输单", "CLOSED", "关闭");
        rows.forEach(row -> row.setStageName(names.getOrDefault(row.getStageCode(), row.getStageCode())));
        return rows;
    }

    @Transactional
    public void evidence(AuthPrincipal actor,Long id,OpportunityRequests.Evidence request) {
        requireUsable(actor);
        Opportunity o=writable(actor,id,request.version(),false); requireActive(o);
        String before=snapshot(o); LocalDateTime now=now(); int changed;
        if ("QUOTE".equals(request.kind())) {
            if ("NEW".equals(o.getStageCode()) || follows.latestEffectiveAtForOpportunity(id) == null) throw new BizException(ErrorCode.OPPORTUNITY_FOLLOW_REQUIRED);
            if (request.quoteAmount()==null) throw new BizException(ErrorCode.VALIDATION_ERROR);
            changed=opportunities.quote(id,o.getVersion(),money(request.quoteAmount()),now);
        } else {
            if (!List.of("QUOTED", "DEAL").contains(o.getStageCode())) throw new BizException(ErrorCode.OPPORTUNITY_QUOTE_REQUIRED);
            if (follows.latestEffectiveAtForOpportunity(id) == null) throw new BizException(ErrorCode.OPPORTUNITY_FOLLOW_REQUIRED);
            if (o.getQuoteAt()==null) throw new BizException(ErrorCode.OPPORTUNITY_QUOTE_REQUIRED);
            changed=opportunities.confirmation(id,o.getVersion(),"CONFIRMED",now);
        }
        if (changed!=1) throw new BizException(ErrorCode.CONFLICT);
        audit(actor,id,"OPPORTUNITY_"+request.kind()+"_RECORDED",before,snapshot(opportunities.lock(id)),request.description().trim());
        advanceFromBusiness(id);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> members(AuthPrincipal actor,Long id) {
        OpportunitySummaryVO row=visible(actor,id);
        if (!Boolean.TRUE.equals(row.getCanManageCollaborators())) throw new BizException(ErrorCode.FORBIDDEN);
        return opportunities.members(row.getTeamId());
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> owners(AuthPrincipal actor) { return opportunities.owners(DataScope.of(actor)); }

    @Transactional
    public void decidePending(AuthPrincipal actor,Long id,OpportunityRequests.PendingDecision request) {
        requireUsable(actor);
        if (!List.of("ADMIN","MANAGER").contains(actor.roleCode())) throw new BizException(ErrorCode.FORBIDDEN);
        Opportunity o=writable(actor,id,request.version(),true);
        if (!"CLOSE_PENDING".equals(o.getActivityStatus())) throw new BizException(ErrorCode.OPPORTUNITY_RULE);
        if (request.close()) { changeStage(actor,id,new OpportunityRequests.StageAction(o.getVersion(),"CLOSED",request.reason())); return; }
        String before=snapshot(o); LocalDateTime now=now();
        if (opportunities.resume(id,o.getVersion(),now)!=1) throw new BizException(ErrorCode.CONFLICT);
        opportunities.resolveTodos(id,now);
        audit(actor,id,"OPPORTUNITY_CLOSE_REJECTED",before,snapshot(opportunities.lock(id)),request.reason());
    }

    private Opportunity writable(AuthPrincipal actor, Long id, long version, boolean management) {
        OpportunitySummaryVO row = visible(actor, id);
        boolean manager = "MANAGER".equals(actor.roleCode()) && Objects.equals(actor.teamId(), row.getTeamId());
        boolean owner = actor.id().equals(row.getOwnerId());
        if (!(owner || manager || "ADMIN".equals(actor.roleCode()))) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        Customer customer=customers.lock(row.getCustomerId());
        if (customer==null || !"PRIVATE".equals(customer.getSeaType())) throw new BizException(ErrorCode.OPPORTUNITY_RULE);
        Opportunity opportunity = opportunities.lock(id);
        if (opportunity == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (!Objects.equals(opportunity.getVersion(), version)) throw new BizException(ErrorCode.CONFLICT);
        return opportunity;
    }

    private OpportunitySummaryVO visible(AuthPrincipal actor, Long id) {
        OpportunitySummaryVO summary = opportunities.summary(id, DataScope.of(actor));
        if (summary == null) throw new BizException(ErrorCode.NOT_FOUND);
        decorate(actor, summary);
        return summary;
    }

    private void decorate(AuthPrincipal actor, OpportunitySummaryVO row) {
        boolean admin = "ADMIN".equals(actor.roleCode());
        boolean manager = "MANAGER".equals(actor.roleCode()) && Objects.equals(actor.teamId(), row.getTeamId());
        boolean owner = actor.id().equals(row.getOwnerId());
        row.setCanEdit(admin || manager || owner);
        row.setCanManageCollaborators(admin || manager || owner);
        row.setCanSetThreshold(admin || manager);
        row.setCanChangeStage(admin && ACTIVE_STAGES.contains(row.getStageCode()));
        row.setCanDecidePending((admin || manager) && "CLOSE_PENDING".equals(row.getActivityStatus()));
    }

    /** 页面和所有推进入口共享门槛；待查验有效记录按 BD-14 暂时计入。 */
    private List<OpportunityStageProgressVO.Requirement> requirements(Opportunity o, String target) {
        List<OpportunityStageProgressVO.Requirement> result = new ArrayList<>();
        int index = STAGE_ORDER.indexOf(target);
        if (index >= 1) result.add(new OpportunityStageProgressVO.Requirement("EFFECTIVE_FOLLOW",
                "本商机至少一条有效跟进（待查验暂时计入，打回撤销）", follows.latestEffectiveAtForOpportunity(o.getId()) != null,
                "在本商机提交方式、联系人、实质结果、下一步动作及未来时间完整的有效跟进"));
        if (index >= 2) result.add(new OpportunityStageProgressVO.Requirement("QUOTE", "已登记报价金额和报价说明",
                o.getQuoteAt() != null && o.getQuoteAmount() != null, "负责人或本团队经理登记报价记录"));
        if (index >= 3) result.add(new OpportunityStageProgressVO.Requirement("CUSTOMER_CONFIRMATION", "已登记客户明确合作确认",
                o.getCustomerConfirmAt() != null && "CONFIRMED".equals(o.getCustomerConfirmResult()), "报价后登记客户合作确认及说明"));
        if (index >= 4) {
            result.add(new OpportunityStageProgressVO.Requirement("CONTRACT_DETAILS", "合同编号、双方主体、金额及实际签订日期齐全",
                    opportunities.completeContractDetails(o.getId()) > 0, "在合同登记中补充真实签约信息；内部编号由系统生成，正式编号按实际文件补录"));
            result.add(new OpportunityStageProgressVO.Requirement("ACTIVE_CONTRACT", "存在已签署且有效的合同登记",
                    opportunities.activeContracts(o.getId()) > 0, "从成交商机创建合同登记草稿，核对实际合同信息后提交核验"));
            result.add(new OpportunityStageProgressVO.Requirement("SIGNED_MATERIAL", "已上传签署合同资料且核验通过",
                    opportunities.verifiedSignedMaterials(o.getId()) > 0, "上传实际签署文件，由本团队经理或管理员核验"));
            result.add(new OpportunityStageProgressVO.Requirement("SIGNING_REVIEW", "经理或管理员已确认签约事实",
                    o.getSignedConfirmedAt() != null, "核验通过后系统登记确认事实并自动推进赢单"));
        }
        return result;
    }

    private List<OpportunityStageProgressVO> stageProgress(Opportunity o) {
        Map<String, String> descriptions = Map.of("NEW", "已建立销售机会，尚需实质沟通", "FOLLOWING", "已进行有效沟通，准备报价",
                "QUOTED", "已登记报价，等待客户确认合作", "DEAL", "客户已确认合作，尚需实际签署及核验",
                "WON", "签约事实已核验；赢单不代表回款结清", "LOST", "机会已输单，保留历史且不可重开", "CLOSED", "机会已关闭，保留历史且不可重开");
        List<OpportunityStageProgressVO> result = new ArrayList<>();
        int currentIndex = STAGE_ORDER.indexOf(o.getStageCode());
        for (String stage : List.of("NEW", "FOLLOWING", "QUOTED", "DEAL", "WON", "LOST", "CLOSED")) {
            int index = STAGE_ORDER.indexOf(stage);
            boolean current = stage.equals(o.getStageCode());
            String next = index >= 0 && index < 4 ? STAGE_ORDER.get(index + 1) : null;
            var gates = next == null ? List.<OpportunityStageProgressVO.Requirement>of() : requirements(o, next);
            String status = current ? "CURRENT" : index >= 0 && currentIndex > index ? "PASSED" : "PENDING";
            result.add(new OpportunityStageProgressVO(stage, STAGE_NAMES.get(stage), descriptions.get(stage), current,
                    status, next, next == null ? null : STAGE_NAMES.get(next), gates,
                    next != null && gates.stream().allMatch(OpportunityStageProgressVO.Requirement::met)));
        }
        return result;
    }

    private void validateStage(String from, String to, String reason, Opportunity opportunity) {
        if (from == null || to.equals(from)) throw new BizException(ErrorCode.OPPORTUNITY_RULE);
        if (!ACTIVE_STAGES.contains(from)) throw new BizException(ErrorCode.OPPORTUNITY_RULE);
        boolean terminal = List.of("LOST", "CLOSED").contains(to);
        if ("WON".equals(to) && !"DEAL".equals(from)) throw new BizException(ErrorCode.OPPORTUNITY_RULE);
        if (terminal && (opportunities.activeContracts(opportunity.getId())>0 || opportunities.receivedPayments(opportunity.getId())>0)) throw new BizException(ErrorCode.OPPORTUNITY_RULE);
        if (!terminal && !"WON".equals(to)) {
            int distance = Math.abs(STAGE_ORDER.indexOf(to) - STAGE_ORDER.indexOf(from));
            if (distance != 1) throw new BizException(ErrorCode.OPPORTUNITY_RULE);
            boolean rollback = STAGE_ORDER.indexOf(to) < STAGE_ORDER.indexOf(from);
            if (rollback && text(reason) == null) throw new BizException(ErrorCode.VALIDATION_ERROR);
            if (rollback && (opportunities.activeContracts(opportunity.getId()) > 0 || opportunities.receivedPayments(opportunity.getId()) > 0)) {
                throw new BizException(ErrorCode.OPPORTUNITY_RULE);
            }
        }
        if (terminal && text(reason) == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private OpportunityStageLog stageLog(Long id, String from, String to, String action, String reason, AuthPrincipal actor, LocalDateTime now) {
        OpportunityStageLog log = new OpportunityStageLog();
        log.setOpportunityId(id); log.setFromStage(from); log.setToStage(to); log.setActionCode(action); log.setReason(reason);
        log.setOperatorId(actor == null ? null : actor.id()); log.setOccurredAt(now); return log;
    }

    private void audit(AuthPrincipal actor, Long id, String action, String before, String after, String reason) {
        opportunities.audit(id, action, actor == null ? null : actor.id(), actor == null ? null : actor.teamId(), now(), before, after, reason);
    }

    private void requireUsable(AuthPrincipal actor) {
        if (actor == null || actor.mustChangePassword()) throw new BizException(ErrorCode.PASSWORD_CHANGE_REQUIRED);
    }

    private void requireActive(Opportunity o) {
        if (!ACTIVE_STAGES.contains(o.getStageCode())) throw new BizException(ErrorCode.OPPORTUNITY_RULE);
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new BizException(ErrorCode.VALIDATION_ERROR);
        return value.setScale(2);
    }

    private LocalDateTime now() { return LocalDateTime.now(businessClock); }
    private String text(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private String snapshot(Opportunity opportunity) {
        try {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", opportunity.getId()); value.put("stageCode", opportunity.getStageCode());
            value.put("opportunityName", opportunity.getOpportunityName()); value.put("expectedAmount", opportunity.getExpectedAmount());
            value.put("expectedCloseDate", opportunity.getExpectedCloseDate()); value.put("stagnationDays", opportunity.getStagnationDays());
            value.put("graceDays", opportunity.getGraceDays()); value.put("activityStatus", opportunity.getActivityStatus());
            value.put("quoteAmount",opportunity.getQuoteAmount()); value.put("quoteAt",opportunity.getQuoteAt());
            value.put("customerConfirmResult",opportunity.getCustomerConfirmResult()); value.put("customerConfirmAt",opportunity.getCustomerConfirmAt());
            return json.writeValueAsString(value);
        } catch (Exception e) { throw new IllegalStateException("无法记录商机审计", e); }
    }

}
