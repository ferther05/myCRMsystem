/* 文件作用：执行合同回款计划、到账核销、终止审批及结清联动。 */
package com.bishe.crm.module.contract.service;

import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.auth.security.DataScope;
import com.bishe.crm.module.contract.dto.PaymentRequests;
import com.bishe.crm.module.contract.entity.Contract;
import com.bishe.crm.module.contract.entity.PaymentPeriod;
import com.bishe.crm.module.contract.entity.PaymentPlan;
import com.bishe.crm.module.contract.mapper.PaymentMapper;
import com.bishe.crm.module.contract.vo.PaymentOverviewVO;
import com.bishe.crm.module.customer.mapper.CustomerMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 合同资金动作保持单合同事务和完整审计。 */
@Service
@RequiredArgsConstructor
public class PaymentService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final PaymentMapper mapper;
    private final CustomerMapper customers;
    private final Clock businessClock;

    /** 查询计划版本、期次、到账和终止申请。 */
    @Transactional(readOnly = true)
    public PaymentOverviewVO overview(AuthPrincipal actor, Long contractId) {
        usable(actor);
        Contract c = requireContract(actor, contractId, false);
        List<PaymentOverviewVO.Plan> plans = mapper.plans(contractId).stream().map(row -> {
            Long id = number(row.get("id"));
            List<PaymentOverviewVO.Period> periods = mapper.periods(id).stream().map(period -> {
                BigDecimal received = money(period.getReceivedAmount());
                return new PaymentOverviewVO.Period(
                    period.getId(), period.getPeriodNo(), period.getPlannedAmount(), received,
                    period.getPlannedAmount().subtract(received).max(ZERO), period.getDueDate(),
                    period.getTriggerNode(), period.getPeriodStatus(), received.signum() > 0, period.getReplacedByPlanId()
                );
            }).toList();
            return new PaymentOverviewVO.Plan(
                id, integer(row.get("version_no")), money(row.get("total_planned_amount")),
                (String) row.get("change_description"), (String) row.get("approval_status"),
                Boolean.TRUE.equals(number(row.get("is_current")) != null && number(row.get("is_current")) == 1),
                time(row.get("submitted_at")), (String) row.get("submitter_name"),
                time(row.get("approved_at")), (String) row.get("approver_name"), number(row.get("version")), periods
            );
        }).toList();
        List<PaymentOverviewVO.Receipt> receipts = mapper.receipts(contractId).stream().map(row ->
            new PaymentOverviewVO.Receipt(
                number(row.get("id")), number(row.get("period_id")), money(row.get("received_amount")),
                time(row.get("received_at")), (String) row.get("payment_method"),
                number(row.get("voucher_attachment_id")), (String) row.get("remark"),
                (String) row.get("difference_description"), (String) row.get("registered_by_name"),
                time(row.get("created_at")), row.get("voided_at") != null,
                (String) row.get("void_reason"), (String) row.get("voided_by_name"),
                time(row.get("voided_at")), number(row.get("version"))
            )
        ).toList();
        Map<String, Object> termination = mapper.termination(contractId);
        boolean manager = isManagerOf(actor, c);
        boolean owner = Objects.equals(actor.id(), c.getOwnerId());
        boolean unsettled = !Set.of("SETTLED", "TERMINATED", "VOID").contains(c.getContractStatus());
        PaymentPlan current = mapper.currentPlan(contractId);
        return new PaymentOverviewVO(
            contractId, c.getPaymentStatus(), money(c.getContractAmount()), money(c.getReceivedAmount()),
            money(c.getOutstandingAmount()), plans, receipts,
            termination == null ? null : new PaymentOverviewVO.Termination(
                number(termination.get("id")), (String) termination.get("approval_status"),
                (String) termination.get("applicant_reason"), (String) termination.get("no_receivable_explanation"),
                (String) termination.get("applicant_name"), time(termination.get("submitted_at")),
                (String) termination.get("approver_name"), (String) termination.get("reason"),
                time(termination.get("processed_at")), number(termination.get("version"))
            ),
            owner && unsettled && c.getRegistrationApprovedAt() != null && Set.of("SIGNED", "PERFORMING").contains(c.getContractStatus()), (manager || admin(actor)) && unsettled,
            owner && unsettled && current != null && "ACTIVE".equals(current.getApprovalStatus()) && Set.of("SIGNED", "PERFORMING").contains(c.getContractStatus()),
            admin(actor), unsettled && money(c.getReceivedAmount()).signum() == 0 && (termination == null || !"PENDING".equals(termination.get("approval_status"))) && (owner || manager || admin(actor)),
            (manager || admin(actor)) && unsettled
        );
    }

    /** 新建计划草稿或替换尚未提交的草稿期次。 */
    @Transactional
    public Long savePlan(AuthPrincipal actor, Long contractId, Long planId, PaymentRequests.Plan request) {
        usable(actor);
        Contract c = lockContract(actor, contractId);
        ownerOnly(actor, c);
        ensureUnsettled(c);
        if (!Set.of("SIGNED", "PERFORMING").contains(c.getContractStatus())) throw rule();
        if (c.getRegistrationApprovedAt() == null) throw rule();
        BigDecimal sum = request.periods().stream().map(p -> scale(p.plannedAmount())).reduce(ZERO, BigDecimal::add);
        BigDecimal expected = c.getCurrentPlanId() == null
            ? money(c.getContractAmount())
            : money(c.getContractAmount()).subtract(mapper.totalReceived(contractId)).setScale(2);
        if (sum.compareTo(expected) != 0) throw rule();
        String description = trim(request.changeDescription());
        if (c.getCurrentPlanId() != null && description == null) throw rule();
        LocalDateTime now = now();
        PaymentPlan plan;
        if (planId == null) {
            plan = new PaymentPlan();
            plan.setContractId(contractId);
            plan.setVersionNo(mapper.nextPlanVersion(contractId));
            plan.setTotalPlannedAmount(sum);
            plan.setChangeDescription(description);
            mapper.insertPlan(plan, actor.id(), now);
        } else {
            plan = mapper.lockPlan(planId, contractId);
            if (plan == null || !Set.of("DRAFT", "RETURNED").contains(plan.getApprovalStatus()) || Boolean.TRUE.equals(plan.getIsCurrent())) throw rule();
            if (!Objects.equals(request.version(), plan.getVersion())) throw conflict();
            mapper.deleteDraftPeriods(planId);
            if (mapper.updateDraftPlan(planId, sum, description, plan.getVersion(), now) != 1) throw conflict();
        }
        for (int i = 0; i < request.periods().size(); i++) {
            PaymentRequests.Period source = request.periods().get(i);
            if (source.dueDate().isBefore(LocalDate.of(2000, 1, 1))) throw rule();
            PaymentPeriod period = new PaymentPeriod();
            period.setPlanId(plan.getId());
            period.setPeriodNo(i + 1);
            period.setPlannedAmount(scale(source.plannedAmount()));
            period.setDueDate(source.dueDate());
            period.setTriggerNode(source.triggerNode());
            if (mapper.insertPeriod(period, now) != 1) throw conflict();
        }
        audit(actor, contractId, "PAYMENT_PLAN_SAVED", null,
            "{\"planId\":" + plan.getId() + ",\"versionNo\":" + plan.getVersionNo() + ",\"total\":\"" + sum + "\"}",
            description == null ? "保存首版回款计划" : description);
        return plan.getId();
    }

    /** 将计划提交给同团队经理确认。 */
    @Transactional
    public void submitPlan(AuthPrincipal actor, Long contractId, Long planId, PaymentRequests.Submit request) {
        usable(actor);
        Contract c = lockContract(actor, contractId);
        ownerOnly(actor, c);
        ensureUnsettled(c);
        PaymentPlan plan = mapper.lockPlan(planId, contractId);
        if (plan == null || !Set.of("DRAFT", "RETURNED").contains(plan.getApprovalStatus())) throw rule();
        if (!Objects.equals(plan.getVersion(), request.version())) throw conflict();
        if (mapper.periods(planId).isEmpty()) throw rule();
        LocalDateTime now = now();
        mapper.insertApproval("PAYMENT_PLAN", contractId, planId, actor.id(), "提交回款计划待经理确认", now, null);
        Long approvalId = mapper.lastInsertId();
        if (approvalId == null) throw conflict();
        mapper.insertApprovalTodos(approvalId, c.getTeamId(), now.toLocalDate(), "合同回款计划待审核");
        if (approvalId == null || mapper.submitPlan(planId, contractId, approvalId, actor.id(), plan.getVersion(), now) != 1) throw conflict();
        audit(actor, contractId, "PAYMENT_PLAN_SUBMITTED", "{\"planId\":" + planId + "}", "{\"approvalId\":" + approvalId + "}", "提交回款计划确认");
    }

    /** 经理或管理员确认计划，只有确认后才替换当前版本。 */
    @Transactional
    public void reviewPlan(AuthPrincipal actor, Long contractId, Long planId, PaymentRequests.Review request) {
        usable(actor);
        Contract c = lockContract(actor, contractId);
        reviewer(actor, c);
        ensureUnsettled(c);
        PaymentPlan plan = mapper.lockPlan(planId, contractId);
        if (plan == null || !"PENDING".equals(plan.getApprovalStatus())) throw rule();
        if (!Objects.equals(plan.getVersion(), request.version())) throw conflict();
        if (Objects.equals(plan.getSubmittedBy(), actor.id())) throw forbidden();
        LocalDateTime now = now();
        Map<String, Object> approval = mapper.lockApproval(plan.getApprovalId(), "PAYMENT_PLAN");
        if (approval == null || !"PENDING".equals(approval.get("approval_status"))) throw conflict();
        Long approvalVersion = number(approval.get("version"));
        String status = request.pass() ? "APPROVED" : "REJECTED";
        if (mapper.reviewApproval(plan.getApprovalId(), actor.id(), status, request.opinion().trim(), approvalVersion, now) != 1) throw conflict();
        mapper.closeApprovalTodos(plan.getApprovalId(), actor.id(), now);
        if (mapper.reviewPlan(planId, contractId, actor.id(), request.pass() ? "APPROVED" : "RETURNED", plan.getVersion(), now) != 1) throw conflict();
        if (request.pass()) {
            BigDecimal required = c.getCurrentPlanId() == null ? money(c.getContractAmount()) : money(c.getContractAmount()).subtract(mapper.totalReceived(contractId)).setScale(2);
            BigDecimal actual = mapper.periods(planId).stream().map(p -> money(p.getPlannedAmount())).reduce(ZERO, BigDecimal::add);
            if (required.compareTo(actual) != 0) throw rule();
            if (c.getCurrentPlanId() != null) {
                for (PaymentPeriod old : mapper.periods(c.getCurrentPlanId())) mapper.resolveOverdueTodos(old.getId(), now);
            }
            mapper.replaceUnpaidPeriods(contractId, planId, now);
            mapper.supersedeCurrentPlan(contractId, now);
            if (mapper.activatePlan(planId, contractId, now) != 1) throw conflict();
            refreshSummary(actor, c, planId, now);
        }
        audit(actor, contractId, "PAYMENT_PLAN_REVIEWED", "{\"planId\":" + planId + "}", "{\"status\":\"" + (request.pass() ? "APPROVED" : "RETURNED") + "\"}", request.opinion().trim());
    }

    /** 核销一笔实际到账，锁合同和期次防止超收、双重核销。 */
    @Transactional
    public void registerReceipt(AuthPrincipal actor, Long contractId, PaymentRequests.Receipt request) {
        usable(actor);
        Contract c = lockContract(actor, contractId);
        ownerOnly(actor, c);
        ensureUnsettled(c);
        if (c.getCurrentPlanId() == null) throw rule();
        if (!Set.of("SIGNED", "PERFORMING").contains(c.getContractStatus())) throw rule();
        if (mapper.receiptKeyExists(request.idempotencyKey().trim()) > 0) throw conflict();
        if (request.voucherAttachmentId() != null && mapper.ownsAttachment(request.voucherAttachmentId(), contractId) != 1) throw rule();
        if (request.receivedAt().isAfter(now())) throw rule();
        Map<String, Object> row = mapper.lockPeriod(request.periodId(), contractId);
        if (row == null || !Objects.equals(number(row.get("is_current")), 1L) || !"ACTIVE".equals(row.get("plan_status"))) throw rule();
        BigDecimal periodPlanned = money(row.get("planned_amount"));
        BigDecimal periodReceived = money(mapper.periodReceived(request.periodId()));
        BigDecimal amount = scale(request.amount());
        if (periodReceived.add(amount).compareTo(periodPlanned) > 0 || amount.compareTo(money(c.getOutstandingAmount())) > 0) throw rule();
        BigDecimal difference = periodPlanned.subtract(periodReceived).subtract(amount).abs();
        if (difference.signum() > 0 && trim(request.differenceDescription()) == null) throw rule();
        LocalDateTime now = now();
        if (mapper.insertReceipt(contractId, request.periodId(), amount, request.receivedAt(), trim(request.paymentMethod()), request.voucherAttachmentId(), actor.id(), trim(request.remark()), trim(request.differenceDescription()), request.idempotencyKey().trim(), now) != 1) throw conflict();
        BigDecimal newPeriodReceived = periodReceived.add(amount).setScale(2);
        LocalDate due = localDate(row.get("due_date"));
        String periodStatus = newPeriodReceived.compareTo(periodPlanned) >= 0 ? "PAID" : due.isBefore(LocalDate.now(businessClock)) ? "OVERDUE" : "PARTIAL";
        if (mapper.updatePeriod(request.periodId(), newPeriodReceived, periodStatus, number(row.get("version")), now) != 1) throw conflict();
        if ("PAID".equals(periodStatus)) mapper.resolveOverdueTodos(request.periodId(), now);
        refreshSummary(actor, c, null, now);
        audit(actor, contractId, "PAYMENT_RECEIPT_CREATED", null, "{\"periodId\":" + request.periodId() + ",\"amount\":\"" + amount + "\"}", "登记实际到账");
    }

    /** 管理员作废误录并重算汇总，原到账明细及原因仍可查。 */
    @Transactional
    public void voidReceipt(AuthPrincipal actor, Long contractId, Long receiptId, PaymentRequests.VoidReceipt request) {
        usable(actor);
        if (!admin(actor)) throw forbidden();
        Contract c = lockContract(actor, contractId);
        Map<String, Object> row = mapper.lockReceipt(receiptId, contractId);
        if (row == null || row.get("voided_at") != null) throw rule();
        if (!Objects.equals(number(row.get("version")), request.version())) throw conflict();
        Long periodId = number(row.get("period_id"));
        Map<String, Object> period = mapper.lockPeriod(periodId, contractId);
        if (period == null) throw rule();
        LocalDateTime now = now();
        if (mapper.voidReceipt(receiptId, contractId, actor.id(), now, request.reason().trim(), request.version()) != 1) throw conflict();
        BigDecimal received = money(mapper.periodReceived(periodId));
        BigDecimal planned = money(period.get("planned_amount"));
        LocalDate due = localDate(period.get("due_date"));
        String status = received.compareTo(planned) >= 0 ? "PAID" : due.isBefore(LocalDate.now(businessClock)) ? "OVERDUE" : received.signum() > 0 ? "PARTIAL" : "UNPAID";
        if (mapper.updatePeriod(periodId, received, status, number(period.get("version")), now) != 1) throw conflict();
        if (!Objects.equals(number(period.get("plan_id")), c.getCurrentPlanId())) {
            mapper.suspendCurrentPlan(contractId, now);
            for (PaymentPeriod currentPeriod : mapper.periods(c.getCurrentPlanId())) mapper.resolveOverdueTodos(currentPeriod.getId(), now);
            audit(actor, contractId, "PAYMENT_PLAN_SUSPENDED", null, null, "历史到账作废，需按最新余额重新编制回款计划并审批");
        }
        refreshSummary(actor, c, null, now);
        if (Objects.equals(number(period.get("plan_id")), c.getCurrentPlanId()) && "OVERDUE".equals(status)) {
            scanOverduePeriod(contractId, periodId, c.getCurrentPlanId(), c.getOwnerId(), c.getTeamId(), LocalDate.now(businessClock), "payment-void:" + receiptId);
        }
        mapper.protectCustomer(c.getCustomerId(), now);
        mapper.finalizeCustomer(c.getCustomerId(), now);
        audit(actor, contractId, "PAYMENT_RECEIPT_VOIDED", "{\"receiptId\":" + receiptId + "}", null, request.reason().trim());
    }

    /** 发起无应收终止申请；实际有收款的合同走结清流程。 */
    @Transactional
    public void requestTermination(AuthPrincipal actor, Long contractId, PaymentRequests.Termination request) {
        usable(actor);
        Contract c = lockContract(actor, contractId);
        ownerOrManager(actor, c);
        ensureUnsettled(c);
        if (mapper.totalReceived(contractId).signum() != 0 || mapper.termination(contractId) != null && "PENDING".equals(mapper.termination(contractId).get("approval_status"))) throw rule();
        LocalDateTime now = now();
        mapper.insertTerminationApproval(contractId, actor.id(), request.reason().trim(), request.noReceivableExplanation().trim(), now, null);
        Long approvalId = mapper.lastInsertId();
        if (approvalId == null) throw conflict();
        mapper.insertApprovalTodos(approvalId, c.getTeamId(), now.toLocalDate(), "合同终止申请待审核");
        audit(actor, contractId, "CONTRACT_TERMINATION_REQUESTED", null, "{\"approvalId\":" + approvalId + "}", request.reason().trim() + "；无应收说明：" + request.noReceivableExplanation().trim());
    }

    /** 由非申请人经理或管理员批准/退回终止申请。 */
    @Transactional
    public void reviewTermination(AuthPrincipal actor, Long contractId, PaymentRequests.Review request) {
        usable(actor);
        Contract c = lockContract(actor, contractId);
        reviewer(actor, c);
        ensureUnsettled(c);
        Map<String, Object> row = mapper.termination(contractId);
        if (row == null || !"PENDING".equals(row.get("approval_status"))) throw rule();
        Long approvalId = number(row.get("id"));
        if (Objects.equals(number(row.get("applicant_id")), actor.id())) throw forbidden();
        Map<String, Object> approval = mapper.lockApproval(approvalId, "CONTRACT_TERMINATION");
        if (approval == null || !Objects.equals(number(approval.get("version")), request.version())) throw conflict();
        LocalDateTime now = now();
        if (mapper.reviewTermination(approvalId, contractId, actor.id(), request.pass() ? "APPROVED" : "REJECTED", request.opinion().trim(), request.version(), now) != 1) throw conflict();
        mapper.closeApprovalTodos(approvalId, actor.id(), now);
        if (request.pass()) {
            if (mapper.totalReceived(contractId).signum() != 0) throw rule();
            for (Map<String, Object> plan : mapper.plans(contractId)) {
                for (PaymentPeriod period : mapper.periods(number(plan.get("id")))) mapper.resolveOverdueTodos(period.getId(), now);
                if ("PENDING".equals(plan.get("approval_status"))) mapper.closeApprovalTodos(number(plan.get("approval_id")), actor.id(), now);
            }
            mapper.cancelPendingPlanApprovals(contractId, now);
            mapper.terminatePlan(contractId, now);
            if (mapper.terminateContract(contractId, approvalId, request.opinion().trim(), c.getVersion(), now) != 1) throw conflict();
            mapper.finalizeCustomer(c.getCustomerId(), now);
        }
        audit(actor, contractId, "CONTRACT_TERMINATION_REVIEWED", "{\"approvalId\":" + approvalId + "}", "{\"status\":\"" + (request.pass() ? "APPROVED" : "REJECTED") + "\"}", request.opinion().trim());
    }

    /** 供签约文件最终确认后调用，处理“先收齐、后签署确认”的合同。 */
    @Transactional
    public void refreshAfterSigning(Long contractId) {
        Long customerId = mapper.customerId(contractId);
        if (customerId != null) customers.lock(customerId);
        Contract c = mapper.lockContract(contractId, new DataScope("ADMIN", null, null));
        if (c != null) refreshSummary(null, c, null, now());
    }

    /** 最后一条商机结束后，再检查已结束合同是否满足客户最终成交条件。 */
    @Transactional
    public void refreshCustomerCompletion(Long customerId) {
        if (customers.lock(customerId) != null) mapper.finalizeCustomer(customerId, now());
    }

    /** 定时任务事务内复核到期事实、更新状态并生成每日提醒。 */
    @Transactional
    public void scanOverduePeriod(Long contractId, Long periodId, Long planId, Long ownerId, Long teamId, LocalDate date, String runId) {
        Long customerId = mapper.customerId(contractId);
        if (customerId == null || customers.lock(customerId) == null) return;
        Contract c = mapper.lockContract(contractId, new DataScope("ADMIN", null, null));
        if (c == null || !Set.of("SIGNED", "PERFORMING").contains(c.getContractStatus())) return;
        Map<String, Object> p = mapper.lockPeriod(periodId, contractId);
        if (p == null || !Objects.equals(number(p.get("is_current")), 1L) || !"ACTIVE".equals(p.get("plan_status"))) return;
        LocalDate due = localDate(p.get("due_date"));
        BigDecimal received = money(mapper.periodReceived(periodId));
        BigDecimal planned = money(p.get("planned_amount"));
        if (due == null || !due.isBefore(date) || received.compareTo(planned) >= 0) return;
        LocalDateTime now = now();
        mapper.markOverdue(periodId, now);
        if (!"OVERDUE".equals(c.getPaymentStatus())) refreshSummary(null, c, null, now);
        String occurrence = "plan-" + number(p.get("plan_id")) + "-period-" + periodId;
        mapper.insertOverdueTodo(periodId, occurrence, date, c.getOwnerId(), "合同回款期次已逾期", runId);
        Long managerId = mapper.teamManager(c.getTeamId());
        if (managerId != null && !Objects.equals(managerId, c.getOwnerId())) mapper.insertOverdueTodo(periodId, occurrence, date, managerId, "团队合同回款期次已逾期", runId);
    }

    /** 将到账、逾期及签署状态聚合回合同。 */
    @Transactional
    public void refreshSummary(AuthPrincipal actor, Contract c, Long planId, LocalDateTime now) {
        if (Set.of("TERMINATED", "VOID").contains(c.getContractStatus())) return;
        BigDecimal received = money(mapper.totalReceived(c.getId()));
        BigDecimal outstanding = money(c.getContractAmount()).subtract(received).max(ZERO).setScale(2);
        int overdue = mapper.overdueCount(c.getId(), LocalDate.now(businessClock));
        String status = outstanding.signum() == 0 && "FILE_CONFIRMED".equals(c.getSigningProgress()) ? "SETTLED"
            : overdue > 0 ? "OVERDUE" : received.signum() == 0 ? "UNPAID"
            : outstanding.signum() == 0 ? "RECEIVED" : "PARTIAL";
        if (mapper.updateContractPayment(c.getId(), received, outstanding, status, planId, c.getVersion(), now) != 1) throw conflict();
        if ("SETTLED".equals(status)) mapper.finalizeCustomer(c.getCustomerId(), now);
    }

    /** 取得有权限的合同并可选择加行锁。 */
    private Contract requireContract(AuthPrincipal actor, Long id, boolean lock) {
        Contract c = lock ? mapper.lockContract(id, DataScope.of(actor)) : mapper.scopedContract(id, DataScope.of(actor));
        if (c == null) throw new BizException(ErrorCode.NOT_FOUND);
        return c;
    }

    private Contract lockContract(AuthPrincipal actor, Long id) {
        Long customerId = mapper.customerId(id);
        if (customerId == null || customers.lock(customerId) == null) throw new BizException(ErrorCode.NOT_FOUND);
        return requireContract(actor, id, true);
    }
    private void reviewer(AuthPrincipal actor, Contract c) { if (!admin(actor) && !isManagerOf(actor, c)) throw forbidden(); }
    private boolean isManagerOf(AuthPrincipal actor, Contract c) { return "MANAGER".equals(actor.roleCode()) && Objects.equals(actor.teamId(), c.getTeamId()); }
    private boolean admin(AuthPrincipal actor) { return "ADMIN".equals(actor.roleCode()); }
    private void ownerOnly(AuthPrincipal actor, Contract c) { if (!Objects.equals(actor.id(), c.getOwnerId())) throw forbidden(); }
    private void ownerOrManager(AuthPrincipal actor, Contract c) { if (!Objects.equals(actor.id(), c.getOwnerId()) && !isManagerOf(actor, c) && !admin(actor)) throw forbidden(); }
    private void ensureUnsettled(Contract c) { if (Set.of("SETTLED", "TERMINATED", "VOID").contains(c.getContractStatus())) throw rule(); }
    private void usable(AuthPrincipal actor) { if (actor == null || actor.mustChangePassword()) throw new BizException(ErrorCode.PASSWORD_CHANGE_REQUIRED); }
    private void audit(AuthPrincipal a, Long id, String action, String before, String after, String reason) { mapper.audit(id, action, a.id(), a.teamId(), now(), before, after, reason); }
    private LocalDateTime now() { return LocalDateTime.now(businessClock); }
    private BigDecimal scale(BigDecimal value) { if (value == null || value.signum() <= 0 || value.scale() > 2) throw new BizException(ErrorCode.VALIDATION_ERROR); return value.setScale(2); }
    private BigDecimal money(BigDecimal value) { return value == null ? ZERO : value.setScale(2); }
    private BigDecimal money(Object value) { return value instanceof BigDecimal b ? money(b) : value instanceof Number n ? new BigDecimal(n.toString()).setScale(2) : ZERO; }
    private Long number(Object value) { return value instanceof Boolean b ? (b ? 1L : 0L) : value instanceof Number n ? n.longValue() : null; }
    private Integer integer(Object value) { return value instanceof Number n ? n.intValue() : null; }
    private LocalDateTime time(Object value) { return value instanceof LocalDateTime t ? t : null; }
    private LocalDate localDate(Object value) { return value instanceof LocalDate d ? d : value instanceof java.sql.Date d ? d.toLocalDate() : null; }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private BizException rule() { return new BizException(ErrorCode.CONTRACT_RULE); }
    private BizException conflict() { return new BizException(ErrorCode.CONFLICT); }
    private BizException forbidden() { return new BizException(ErrorCode.FORBIDDEN); }
}
