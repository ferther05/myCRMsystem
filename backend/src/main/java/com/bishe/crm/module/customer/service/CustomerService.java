package com.bishe.crm.module.customer.service;

import com.bishe.crm.common.api.*;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.customer.dto.*;
import com.bishe.crm.module.customer.entity.*;
import com.bishe.crm.module.customer.mapper.*;
import com.bishe.crm.module.customer.vo.*;
import com.bishe.crm.module.system.mapper.AccountMapper;
import com.bishe.crm.module.system.mapper.SystemConfigMapper;
import com.bishe.crm.module.opportunity.entity.Opportunity;
import com.bishe.crm.module.opportunity.mapper.OpportunityMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class CustomerService {
    private final CustomerMapper customers;
    private final ContactMapper contacts;
    private final FollowRecordMapper follows;
    private final CustomerDictionaryMapper dictionaries;
    private final SystemConfigMapper configs;
    private final AccountMapper accounts;
    private final OpportunityMapper opportunities;
    private final com.bishe.crm.module.opportunity.service.OpportunityService opportunityService;
    private final ObjectMapper json;
    private final Clock businessClock;

    public PageResult<CustomerSummaryVO> list(AuthPrincipal actor, CustomerQuery query) {
        var page = customers.list(query.toPage(), DataScope.of(actor), now(), query);
        page.getRecords().forEach(row -> decorate(actor, row));
        return PageResult.from(page);
    }

    @Transactional(readOnly=true)
    public CustomerDetailVO detail(AuthPrincipal actor, Long id) {
        var summary = visible(actor, id);
        var entity = customers.active(id);
        if (entity == null) throw new BizException(ErrorCode.NOT_FOUND);
        var detail = new CustomerDetailVO();
        BeanUtils.copyProperties(summary, detail);
        detail.setCreatedAt(entity.getCreatedAt());
        // Public detail remains L1 + masked L2, including for administrators.
        boolean privateSea = "PRIVATE".equals(entity.getSeaType());
        detail.setCanManageContacts(privateSea && Boolean.TRUE.equals(summary.getCanEdit()));
        detail.setContacts(privateSea ? contacts.forCustomer(id).stream().map(this::contactVO).toList() : List.of());
        if (privateSea) {
            detail.setUnifiedSocialCreditCode(entity.getUnifiedSocialCreditCode());
            detail.setCompanySize(entity.getCompanySize());
            detail.setRemark(entity.getRemark());
            detail.setLastEffectiveFollowAt(entity.getLastEffectiveFollowAt());
            detail.setFirstContactDeadlineAt(entity.getFirstContactDeadlineAt());
            detail.setFollowRecords(follows.byCustomer(id));
        }
        return detail;
    }

    @Transactional
    public Long addFollow(AuthPrincipal actor, Long id, CustomerRequests.FollowWrite request) {
        begin(actor);
        Customer customer;
        Opportunity opportunity = null;
        boolean narrowCollaborator = request.opportunityId() != null;
        if (narrowCollaborator) {
            customer = customers.lock(id);
            if (customer == null || !"PRIVATE".equals(customer.getSeaType())) throw new BizException(ErrorCode.NOT_FOUND);
            opportunity = opportunities.lock(request.opportunityId());
            if (opportunity == null || !id.equals(opportunity.getCustomerId())) throw new BizException(ErrorCode.NOT_FOUND);
            if (!List.of("NEW","FOLLOWING","QUOTED","DEAL").contains(opportunity.getStageCode())) throw new BizException(ErrorCode.OPPORTUNITY_RULE);
            boolean owner = actor.id().equals(opportunity.getOwnerId());
            boolean manager = "MANAGER".equals(actor.roleCode()) && actor.teamId() != null && actor.teamId().equals(opportunity.getTeamId());
            boolean collaborator = opportunities.activeCollaborator(opportunity.getId(), actor.id()) > 0;
            if (!owner && !manager && !"ADMIN".equals(actor.roleCode()) && !collaborator) throw new BizException(ErrorCode.FORBIDDEN);
            if (!Objects.equals(customer.getVersion(),request.customerVersion())) throw new BizException(ErrorCode.CONFLICT);
        } else {
            customer = writable(actor,id,request.customerVersion());
        }
        if (!"PRIVATE".equals(customer.getSeaType())) throw new BizException(ErrorCode.FORBIDDEN);
        Contact contact = contacts.selectById(request.contactId());
        if (contact == null || !id.equals(contact.getCustomerId())) throw new BizException(ErrorCode.NOT_FOUND);
        LocalDateTime now = now();
        if (!request.nextActionAt().isAfter(now) || request.followedAt().isAfter(now)) throw new BizException(ErrorCode.VALIDATION_ERROR);
        FollowRecord record = new FollowRecord();
        record.setCustomerId(id); record.setOpportunityId(request.opportunityId()); record.setContactId(contact.getId()); record.setFollowUserId(actor.id());
        record.setRecordType("FOLLOW"); record.setFollowMethod(request.followMethod()); record.setContent(request.content().trim());
        record.setResultCode(request.resultCode()); record.setSubstantiveResult(request.substantiveResult().trim());
        record.setNextAction(request.nextAction().trim()); record.setNextActionAt(request.nextActionAt()); record.setFollowedAt(request.followedAt());
        boolean effective = !List.of("NO_RESPONSE","NOT_CONNECTED").contains(request.resultCode());
        if ("OTHER".equals(request.resultCode()) && request.substantiveResult().trim().length() < 8) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        record.setIsEffective(effective); record.setReviewStatus(effective ? "PENDING" : "NOT_REQUIRED"); record.setCreatedAt(now);
        changed(follows.insertFollow(record));
        if (effective) {
            changed(customers.applyEffectiveFollow(id,customer.getVersion(),request.followedAt(),actor.id(),now));
            customer.setLastEffectiveFollowAt(request.followedAt()); customer.setVersion(customer.getVersion()+1);
            if (opportunity != null) {
                changed(opportunities.touchProgress(opportunity.getId(),opportunity.getVersion(),request.followedAt(),now));
                opportunities.resolveTodos(opportunity.getId(),now);
                opportunityService.advanceFromBusiness(opportunity.getId());
            }
            audit(actor,customer,"FOLLOW_RECORDED",null,"新增有效跟进记录 " + record.getId() + "，待经理/管理员查验");
        } else {
            audit(actor,customer,"FOLLOW_RECORDED",null,"新增无效跟进留痕 " + record.getId() + "，不刷新有效跟进计时");
        }
        return record.getId();
    }

    @Transactional
    public void reviewFollow(AuthPrincipal actor,Long customerId,Long followId,CustomerRequests.FollowReview request) {
        begin(actor);
        Customer customer = writable(actor,customerId,request.version());
        if (!List.of("ADMIN","MANAGER").contains(actor.roleCode())
                || ("MANAGER".equals(actor.roleCode()) && !Objects.equals(actor.teamId(),customer.getTeamId()))) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        FollowRecord follow = follows.lock(followId,customerId);
        if (follow == null || !"PENDING".equals(follow.getReviewStatus())) throw new BizException(ErrorCode.NOT_FOUND);
        if (follow.getFollowUserId().equals(actor.id())) throw new BizException(ErrorCode.FORBIDDEN);
        if ("RETURNED".equals(request.reviewStatus()) && nullable(request.reviewReason()) == null) throw new BizException(ErrorCode.VALIDATION_ERROR);
        boolean accepted = "CONFIRMED".equals(request.reviewStatus());
        LocalDateTime reviewedAt = now();
        changed(follows.review(followId,follow.getVersion(),request.reviewStatus(),accepted,actor.id(),reviewedAt,nullable(request.reviewReason())));
        if (!accepted) {
            LocalDateTime latest = follows.latestEffectiveAt(customerId);
            changed(customers.restoreFollowClock(customerId,customer.getVersion(),latest,actor.id(),now()));
            customer.setLastEffectiveFollowAt(latest); customer.setVersion(customer.getVersion()+1);
            if (follow.getOpportunityId() != null) {
                Opportunity opportunity = opportunities.lock(follow.getOpportunityId());
                if (opportunity != null && customerId.equals(opportunity.getCustomerId())) {
                    LocalDateTime latestOpportunity = follows.latestEffectiveAtForOpportunity(opportunity.getId());
                    changed(opportunities.restoreProgress(opportunity.getId(),opportunity.getVersion(),latestOpportunity,now()));
                }
            }
        }
        audit(actor,customer,"FOLLOW_REVIEWED",null,"跟进记录 " + followId + (accepted ? "已查验通过" : "已打回并撤销有效性：" + text(request.reviewReason())));
    }

    @Transactional
    public void claim(AuthPrincipal actor, Long id, long version) {
        begin(actor);
        if (!"SPECIALIST".equals(actor.roleCode())) throw new BizException(ErrorCode.FORBIDDEN);
        Customer customer = customers.lock(id);
        if (customer == null || !"PUBLIC".equals(customer.getSeaType()) || customer.getOwnerId() != null) throw new BizException(ErrorCode.NOT_FOUND);
        LocalDateTime now = now();
        boolean priority = actor.id().equals(customer.getPreviousOwnerId()) && customer.getReturnPriorityUntil() != null
                && customer.getReturnPriorityUntil().isAfter(now);
        var claimant = accounts.lockUser(actor.id());
        if (!AuthUsers.usable(claimant)) throw new BizException(ErrorCode.FORBIDDEN);
        if (!priority) {
            int cap = integerConfig("claim_hold_limit",50);
            int dailyLimit = integerConfig("claim_daily_limit",3);
            int held = customers.privateHoldCount(actor.id());
            int used = claimant.getClaimDate() != null && claimant.getClaimDate().equals(now.toLocalDate())
                    ? Optional.ofNullable(claimant.getClaimCount()).orElse(0) : 0;
            if (held >= cap || used >= dailyLimit) throw new BizException(ErrorCode.CUSTOMER_CLAIM_LIMIT);
        }
        if (customer.getVersion() != version) throw new BizException(ErrorCode.CONFLICT);
        LocalDateTime warningAt = now.plusHours(hours("first_contact_warning_hours"));
        LocalDateTime deadlineAt = now.plusHours(hours("first_contact_recycle_hours"));
        String quarter = String.format(Locale.ROOT,"%04dQ%d",now.getYear(),(now.getMonthValue()-1)/3+1);
        changed(customers.claim(id,version,actor.id(),actor.teamId(),warningAt,deadlineAt,quarter,actor.id(),now));
        changed(accounts.recordClaim(actor.id(),now.toLocalDate(),priority));
        accounts.syncHoldCount(actor.id(),customers.privateHoldCount(actor.id()));
        long nextSeq = customer.getOwnershipSeq()+1;
        changed(customers.ownershipAudit(id,priority ? "PRIORITY_CLAIM" : "PUBLIC_CLAIM",actor.id(),actor.teamId(),now,
                nextSeq,customer.getPreviousOwnerId(),actor.id(),actor.teamId(),null,null,null,
                priority ? "原负责人7天优先重新领取" : "专员领取公海客户"));
    }

    @Transactional
    public void transfer(AuthPrincipal actor, Long id, CustomerRequests.OwnershipTransfer request) {
        begin(actor);
        if (!"ADMIN".equals(actor.roleCode())) throw new BizException(ErrorCode.FORBIDDEN);
        Customer customer = customers.lock(id);
        if (customer == null || !"PRIVATE".equals(customer.getSeaType())) throw new BizException(ErrorCode.NOT_FOUND);
        if (customer.getVersion() != request.version()) throw new BizException(ErrorCode.CONFLICT);
        Long oldOwnerId = customer.getOwnerId();
        if (oldOwnerId == null || oldOwnerId.equals(request.newOwnerId())) throw new BizException(ErrorCode.VALIDATION_ERROR);
        Long firstLockId = Math.min(oldOwnerId,request.newOwnerId());
        Long secondLockId = Math.max(oldOwnerId,request.newOwnerId());
        var firstLocked = accounts.lockUser(firstLockId);
        var secondLocked = accounts.lockUser(secondLockId);
        var oldOwner = oldOwnerId.equals(firstLockId) ? firstLocked : secondLocked;
        var newOwner = request.newOwnerId().equals(firstLockId) ? firstLocked : secondLocked;
        if (oldOwner == null || !AuthUsers.usable(newOwner) || !"SPECIALIST".equals(newOwner.getRoleCode())
                || !Objects.equals(oldOwner.getTeamId(),newOwner.getTeamId()) || !Objects.equals(customer.getTeamId(),newOwner.getTeamId())) {
            throw new BizException(ErrorCode.ACCOUNT_RULE);
        }
        LocalDateTime now = now(), readonlyUntil = now.plusDays(7);
        String before = encode(Map.of("ownerId",oldOwnerId,"teamId",customer.getTeamId(),"ownershipSeq",customer.getOwnershipSeq()));
        changed(customers.transfer(id,request.version(),newOwner.getId(),newOwner.getTeamId(),actor.id(),now));
        accounts.syncHoldCount(oldOwnerId,customers.privateHoldCount(oldOwnerId));
        accounts.syncHoldCount(newOwner.getId(),customers.privateHoldCount(newOwner.getId()));
        long nextSeq = customer.getOwnershipSeq()+1;
        String after = encode(Map.of("ownerId",newOwner.getId(),"teamId",newOwner.getTeamId(),"ownershipSeq",nextSeq));
        changed(customers.ownershipAudit(id,"OWNER_TRANSFERRED",actor.id(),actor.teamId(),now,nextSeq,oldOwnerId,
                newOwner.getId(),newOwner.getTeamId(),readonlyUntil,before,after,text(request.reason())));
    }

    @Transactional
    public Long create(AuthPrincipal actor, CustomerRequests.Create request) {
        if (!"ADMIN".equals(actor.roleCode())) throw new BizException(ErrorCode.FORBIDDEN);
        begin(actor);
        Customer customer = new Customer();
        var candidates = dictionaries.owners(DataScope.of(actor));
        Long owner = request.ownerId();
        if (owner != null) {
            final Long selectedOwner = owner;
            var target = candidates.stream().filter(u -> u.getId().equals(selectedOwner)).findFirst()
                    .orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN));
            // Lock the selected account as well: moving/disabling it cannot race customer creation.
            var lockedOwner = accounts.lockUser(owner);
            if (!AuthUsers.usable(lockedOwner) || !List.of("MANAGER", "SPECIALIST").contains(lockedOwner.getRoleCode())
                    || !Objects.equals(lockedOwner.getTeamId(),target.getTeamId())) {
                throw new BizException(ErrorCode.CONFLICT);
            }
            customer.setOwnerId(owner);
            customer.setTeamId(target.getTeamId());
        }
        profile(customer, request.profile(), null);
        customer.setPrimaryPhone(phone(request.primaryContact().phone()));
        customer.setNormalizedPrimaryPhone(phone(request.primaryContact().phone()));
        boolean sameNameReviewed = duplicates(actor,customer,true,request.sameNameConfirmed(),request.reviewReason());
        customer.setSeaType(owner == null ? "PUBLIC" : "PRIVATE");
        customer.setCustomerStatus("NEW");
        customer.setOwnershipSeq(owner == null ? 0L : 1L);
        customer.setVersion(0L); customer.setDeleted(false);
        customer.setCreatedBy(actor.id()); customer.setUpdatedBy(actor.id());
        customer.setCreatedAt(now()); customer.setUpdatedAt(now());
        if (owner != null) {
            customer.setWarningAt(customer.getCreatedAt().plusHours(hours("first_contact_warning_hours")));
            customer.setFirstContactDeadlineAt(customer.getCreatedAt().plusHours(hours("first_contact_recycle_hours")));
        }
        changed(customers.insert(customer));
        Contact contact = contact(request.primaryContact());
        contact.setCustomerId(customer.getId()); contact.setIsPrimary(true); contact.setCreatedBy(actor.id());
        changed(contacts.insert(contact));
        if (owner != null) accounts.syncHoldCount(owner,customers.privateHoldCount(owner));
        audit(actor,customer,"CUSTOMER_CREATED",null,
                sameNameReviewed ? "录入客户；人工同名复核：" + text(request.reviewReason()) : "录入客户并建立主要联系人");
        return customer.getId();
    }

    @Transactional
    public void edit(AuthPrincipal actor, Long id, CustomerRequests.Edit request) {
        begin(actor);
        Customer customer = writable(actor,id,request.version());
        String before = snapshot(customer);
        String oldName = customer.getNormalizedName();
        // Public administration edits only L1/classification. Do not erase hidden L3/notes.
        if ("PUBLIC".equals(customer.getSeaType()) && request.profile().unifiedSocialCreditCode() != null
                && !request.profile().unifiedSocialCreditCode().isBlank()) throw new BizException(ErrorCode.FORBIDDEN);
        String credit = customer.getUnifiedSocialCreditCode(), size = customer.getCompanySize(), remark = customer.getRemark();
        profile(customer,request.profile(),customer);
        if ("PUBLIC".equals(customer.getSeaType())) {
            customer.setUnifiedSocialCreditCode(credit); customer.setCompanySize(size); customer.setRemark(remark);
        }
        boolean sameNameReviewed = duplicates(actor,customer,!Objects.equals(oldName,customer.getNormalizedName()),request.sameNameConfirmed(),request.reviewReason());
        changed(customers.save(customer,actor.id(),now()));
        audit(actor,customer,"CUSTOMER_UPDATED",before,
                sameNameReviewed ? "修改档案；人工同名复核：" + text(request.reviewReason()) : "修改档案；不刷新有效跟进时间");
    }

    @Transactional
    public void classify(AuthPrincipal actor, Long id, CustomerRequests.Classification request) {
        begin(actor);
        Customer customer = writable(actor,id,request.version());
        String before = snapshot(customer);
        validateCode("level",request.customerLevel(),customer.getCustomerLevel());
        validateTags(request.tags(),tags(customer.getTagsJson()));
        customer.setCustomerLevel(request.customerLevel()); customer.setTagsJson(encode(request.tags()));
        changed(customers.save(customer,actor.id(),now()));
        audit(actor,customer,"CUSTOMER_CLASSIFIED",before,"调整分类标签；不刷新有效跟进或回收计时");
    }

    @Transactional
    public void delete(AuthPrincipal actor, Long id, long version) {
        if (!"ADMIN".equals(actor.roleCode())) throw new BizException(ErrorCode.FORBIDDEN);
        begin(actor);
        Customer customer = writable(actor,id,version);
        if (customers.businessLinks(id) != 0) throw new BizException(ErrorCode.CUSTOMER_HAS_HISTORY);
        changed(customers.softDelete(id,version,actor.id(),now()));
        if (customer.getOwnerId() != null) accounts.syncHoldCount(customer.getOwnerId(),customers.privateHoldCount(customer.getOwnerId()));
        audit(actor,customer,"CUSTOMER_DELETED",snapshot(customer),"管理员逻辑删除无关联业务的误录客户，联系人及审计保留");
    }

    @Transactional
    public Long addContact(AuthPrincipal actor, Long id, CustomerRequests.ContactWrite request) {
        begin(actor);
        Customer customer = contactWritable(actor,id,request.customerVersion());
        Contact contact = contact(request.contact());
        contact.setCustomerId(id); contact.setCreatedBy(actor.id());
        contact.setIsPrimary(request.contact().primary() || contacts.forCustomer(id).isEmpty());
        Contact previousPrimary = contact.getIsPrimary() ? contacts.primary(id) : null;
        boolean sameNameReviewed = Boolean.TRUE.equals(contact.getIsPrimary())
                && replacePrimary(actor,customer,contact,request.sameNameConfirmed(),request.reviewReason());
        changed(contacts.insert(contact));
        changed(customers.save(customer,actor.id(),now()));
        auditContacts(actor,customer,"CONTACT_CREATED",previousPrimary == null ? null : List.of(previousPrimary),
                List.of(contact),reviewAudit("新增联系人，编号 " + contact.getId(),sameNameReviewed,request.reviewReason()));
        return contact.getId();
    }

    @Transactional
    public void editContact(AuthPrincipal actor, Long id, Long contactId, CustomerRequests.ContactWrite request) {
        begin(actor);
        Customer customer = contactWritable(actor,id,request.customerVersion());
        Contact previous = contacts.selectById(contactId);
        if (previous == null || !id.equals(previous.getCustomerId())) throw new BizException(ErrorCode.NOT_FOUND);
        if (Boolean.TRUE.equals(previous.getIsPrimary()) && !request.contact().primary()) {
            throw new BizException(ErrorCode.PRIMARY_CONTACT_REQUIRED);
        }
        Contact contact = contact(request.contact()); contact.setId(contactId); contact.setCustomerId(id);
        contact.setVersion(previous.getVersion()+1);
        boolean wasPrimary = Boolean.TRUE.equals(previous.getIsPrimary());
        boolean remainsPrimary = wasPrimary && Boolean.TRUE.equals(contact.getIsPrimary());
        Contact previousPrimary = Boolean.TRUE.equals(contact.getIsPrimary()) ? contacts.primary(id) : null;
        boolean sameNameReviewed = false;
        if (Boolean.TRUE.equals(contact.getIsPrimary())) {
            if (remainsPrimary) {
                // The current primary contact is already the unique primary row. Do not clear it
                // first: clearPrimary increments its version and would make this versioned save
                // fail even though the user is editing the same contact.
                String nextPhone = phone(contact.getPhone());
                boolean duplicateKeyChanged = !Objects.equals(customer.getNormalizedPrimaryPhone(),nextPhone);
                customer.setPrimaryPhone(contact.getPhone());
                customer.setNormalizedPrimaryPhone(nextPhone);
                if (duplicateKeyChanged) sameNameReviewed = duplicates(actor,customer,true,request.sameNameConfirmed(),request.reviewReason());
            } else {
                sameNameReviewed = replacePrimary(actor,customer,contact,request.sameNameConfirmed(),request.reviewReason());
            }
        }
        changed(contacts.save(contact,now(),previous.getVersion()));
        changed(customers.save(customer,actor.id(),now()));
        List<Contact> before = new ArrayList<>();
        if (previousPrimary != null) before.add(previousPrimary);
        if (before.stream().noneMatch(value -> value.getId().equals(previous.getId()))) before.add(previous);
        auditContacts(actor,customer,"CONTACT_UPDATED",before,List.of(contact),
                reviewAudit("更新联系人，编号 " + contactId + "；主要联系人变化同步主电话并查重",sameNameReviewed,request.reviewReason()));
    }

    @Transactional
    public void deleteContact(AuthPrincipal actor, Long id, Long contactId, long version) {
        begin(actor);
        Customer customer = contactWritable(actor,id,version);
        Contact contact = contacts.selectById(contactId);
        if (contact == null || !id.equals(contact.getCustomerId())) throw new BizException(ErrorCode.NOT_FOUND);
        if (Boolean.TRUE.equals(contact.getIsPrimary())) throw new BizException(ErrorCode.PRIMARY_CONTACT_REQUIRED);
        if (contacts.references(contactId) > 0) throw new BizException(ErrorCode.CUSTOMER_HAS_HISTORY);
        changed(contacts.deleteById(contactId));
        changed(customers.save(customer,actor.id(),now()));
        auditContacts(actor,customer,"CONTACT_DELETED",List.of(contact),null,
                "删除无业务关联的普通联系人，编号 " + contactId);
    }

    private boolean replacePrimary(AuthPrincipal actor, Customer customer, Contact contact,
                                boolean sameNameConfirmed, String reviewReason) {
        String nextPhone = phone(contact.getPhone());
        boolean duplicateKeyChanged = !Objects.equals(customer.getNormalizedPrimaryPhone(),nextPhone);
        customer.setPrimaryPhone(contact.getPhone()); customer.setNormalizedPrimaryPhone(nextPhone);
        boolean sameNameReviewed = duplicateKeyChanged && duplicates(actor,customer,true,sameNameConfirmed,reviewReason);
        contacts.clearPrimary(customer.getId(),now());
        return sameNameReviewed;
    }

    private Customer contactWritable(AuthPrincipal actor, Long id, long version) {
        Customer customer = writable(actor,id,version);
        if (!"PRIVATE".equals(customer.getSeaType())) throw new BizException(ErrorCode.FORBIDDEN);
        return customer;
    }

    private Customer writable(AuthPrincipal actor, Long id, long version) {
        var visible = visible(actor,id);
        if (!Boolean.TRUE.equals(visible.getCanEdit())) throw new BizException(ErrorCode.FORBIDDEN);
        Customer customer = customers.lock(id);
        if (customer == null) throw new BizException(ErrorCode.NOT_FOUND);
        // Recheck under the row lock; visibility alone includes the read-only handover exception.
        boolean allowed = "ADMIN".equals(actor.roleCode()) || ("PRIVATE".equals(customer.getSeaType())
                && ("MANAGER".equals(actor.roleCode()) ? Objects.equals(actor.teamId(),customer.getTeamId()) : actor.id().equals(customer.getOwnerId())));
        if (!allowed) throw new BizException(ErrorCode.FORBIDDEN);
        if (customer.getVersion() != version) throw new BizException(ErrorCode.CONFLICT);
        return customer;
    }

    private CustomerSummaryVO visible(AuthPrincipal actor, Long id) {
        var summary = customers.summary(id,DataScope.of(actor),now());
        if (summary == null) throw new BizException(ErrorCode.NOT_FOUND);
        decorate(actor,summary);
        return summary;
    }

    private void decorate(AuthPrincipal actor, CustomerSummaryVO row) {
        row.setTags(tags(row.getTagsJson())); row.setCanDelete("ADMIN".equals(actor.roleCode()));
    }

    void begin(AuthPrincipal actor) {
        var current = accounts.lockUser(actor.id());
        if (!AuthUsers.usable(current) || current.getVersion() != actor.version()
                || Boolean.TRUE.equals(current.getMustChangePassword())) throw new BizException(ErrorCode.TOKEN_INVALID);
        // One existing config row serializes duplicate checks across names/credit/primary-phone changes.
        // At this project's 10-50 users this avoids another lock table. The lock ends with the transaction.
        if (customers.lockDuplicateGuard() == null) throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    private boolean duplicates(AuthPrincipal actor, Customer customer, boolean reviewName, boolean confirmed, String reason) {
        boolean sameName = false;
        for (Customer existing : customers.duplicateCandidates(customer.getId() == null ? 0 : customer.getId(),customer.getNormalizedName(),customer.getUnifiedSocialCreditCode())) {
            if ((customer.getUnifiedSocialCreditCode() != null && customer.getUnifiedSocialCreditCode().equals(existing.getUnifiedSocialCreditCode()))
                    || (customer.getNormalizedName().equals(existing.getNormalizedName())
                    && Objects.equals(customer.getNormalizedPrimaryPhone(),existing.getNormalizedPrimaryPhone()))) {
                throw new BizException(ErrorCode.CUSTOMER_DUPLICATE);
            }
            if (!Boolean.TRUE.equals(existing.getDeleted()) && customer.getNormalizedName().equals(existing.getNormalizedName())) sameName = true;
        }
        if (!reviewName || !sameName) return false;
        if (!confirmed || !List.of("ADMIN","MANAGER").contains(actor.roleCode()) || nullable(reason) == null) {
            throw new BizException(ErrorCode.CUSTOMER_REVIEW_REQUIRED);
        }
        return true;
    }
    private String reviewAudit(String base,boolean reviewed,String reason) {
        return reviewed ? base + "；人工同名复核：" + text(reason) : base;
    }

    private void profile(Customer customer, CustomerRequests.Profile profile, Customer previous) {
        validateCode("source",nullable(profile.sourceCode()),previous == null ? null : previous.getSourceCode());
        validateCode("industry",nullable(profile.industryCode()),previous == null ? null : previous.getIndustryCode());
        validateCode("level",profile.customerLevel(),previous == null ? null : previous.getCustomerLevel());
        validateTags(profile.tags(),previous == null ? List.of() : tags(previous.getTagsJson()));
        customer.setCustomerName(text(profile.customerName())); customer.setNormalizedName(normalizeName(profile.customerName()));
        customer.setUnifiedSocialCreditCode(nullable(profile.unifiedSocialCreditCode()) == null ? null : profile.unifiedSocialCreditCode().toUpperCase(Locale.ROOT));
        customer.setSourceCode(nullable(profile.sourceCode())); customer.setIndustryCode(nullable(profile.industryCode()));
        customer.setRegion(nullable(profile.region())); customer.setCompanySize(nullable(profile.companySize())); customer.setRemark(nullable(profile.remark()));
        customer.setCustomerLevel(profile.customerLevel()); customer.setTagsJson(encode(profile.tags()));
    }

    private void validateCode(String group, String code, String previous) {
        if (code == null) return;
        if (!Objects.equals(code,previous) && dictionaries.all().stream().noneMatch(d -> d.group().equals(group) && d.code().equals(code) && d.enabled())) {
            throw new BizException(ErrorCode.DICTIONARY_INVALID);
        }
    }
    private void validateTags(List<String> values, List<String> previous) {
        if (new HashSet<>(values).size() != values.size()) throw new BizException(ErrorCode.DICTIONARY_INVALID);
        values.forEach(value -> validateCode("tag",value,previous.contains(value) ? value : null));
    }
    private long hours(String key) {
        var query = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.bishe.crm.module.system.entity.SystemConfig>();
        query.eq("config_type","PARAM").eq("config_group","crm.time").eq("config_key",key).eq("enabled",1);
        var config = configs.selectOne(query);
        if (config == null) throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        try { long value = Long.parseLong(config.getConfigValue()); if (value <= 0 || value > 8760) throw new NumberFormatException(); return value; }
        catch (NumberFormatException exception) { throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE); }
    }
    private int integerConfig(String key,int fallback) {
        var query = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.bishe.crm.module.system.entity.SystemConfig>();
        query.eq("config_type","PARAM").eq("config_group","crm.customer").eq("config_key",key).eq("enabled",1);
        var config = configs.selectOne(query);
        if (config == null) return fallback;
        try { int value = Integer.parseInt(config.getConfigValue()); if (value <= 0) throw new NumberFormatException(); return value; }
        catch (NumberFormatException exception) { throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE); }
    }
    private Contact contact(CustomerRequests.ContactInput request) {
        Contact contact = new Contact();
        contact.setContactName(text(request.contactName())); contact.setJobTitle(nullable(request.jobTitle()));
        contact.setPhone(phone(request.phone())); contact.setEmail(nullable(request.email())); contact.setBirthday(request.birthday());
        contact.setDecisionRole(request.decisionRole() == null ? "CONTACT" : request.decisionRole());
        contact.setIsPrimary(request.primary()); contact.setVersion(0L); contact.setCreatedAt(now()); contact.setUpdatedAt(now());
        return contact;
    }
    private ContactVO contactVO(Contact contact) {
        var view = new ContactVO(); BeanUtils.copyProperties(contact,view); view.setPrimary(contact.getIsPrimary()); return view;
    }
    private void audit(AuthPrincipal actor,Customer customer,String action,String before,String reason) {
        auditEvent(actor,customer,action,before,snapshot(customer),reason);
    }
    private void auditContacts(AuthPrincipal actor,Customer customer,String action,List<Contact> before,
                               List<Contact> after,String reason) {
        auditEvent(actor,customer,action,contactStates(before),contactStates(after),reason);
    }
    private void auditEvent(AuthPrincipal actor,Customer customer,String action,String before,String after,String reason) {
        // Review reasons are free text: redact common phone/credit-code patterns before logging.
        String safeReason = reason.replaceAll("[0-9A-Z]{18}","[信用代码已隐藏]")
                .replaceAll("\\+?[0-9][0-9 ()-]{5,}[0-9]","[号码已隐藏]");
        changed(customers.audit(customer.getId(),action,actor.id(),actor.teamId(),now(),customer.getOwnershipSeq(),before,after,safeReason));
    }
    private String contactStates(List<Contact> values) {
        if (values == null) return null;
        Map<Long,Contact> unique = new LinkedHashMap<>();
        values.stream().filter(Objects::nonNull).forEach(value -> unique.put(value.getId(),value));
        List<Map<String,Object>> states = new ArrayList<>();
        for (Contact value : unique.values()) {
            Map<String,Object> state = new LinkedHashMap<>();
            state.put("id",value.getId()); state.put("contactName",value.getContactName());
            state.put("jobTitle",value.getJobTitle()); state.put("phone",maskPhone(value.getPhone()));
            state.put("email",maskEmail(value.getEmail())); state.put("birthday",value.getBirthday());
            state.put("decisionRole",value.getDecisionRole()); state.put("primary",value.getIsPrimary());
            states.add(state);
        }
        return encode(states);
    }
    private String maskPhone(String value) {
        if (value == null) return null;
        String phone = value.replaceAll("\\s","");
        return phone.length() >= 7 ? phone.substring(0,3)+"****"+phone.substring(phone.length()-4) : "****";
    }
    private String maskEmail(String value) {
        if (value == null) return null;
        int at = value.indexOf('@');
        if (at <= 0) return "****";
        return value.substring(0,1)+"***"+value.substring(at);
    }
    private String snapshot(Customer customer) {
        Map<String,Object> snapshot = new LinkedHashMap<>();
        snapshot.put("customerName",customer.getCustomerName()); snapshot.put("customerLevel",customer.getCustomerLevel());
        snapshot.put("tags",tags(customer.getTagsJson())); snapshot.put("sourceCode",customer.getSourceCode());
        snapshot.put("industryCode",customer.getIndustryCode()); snapshot.put("region",customer.getRegion());
        String phone = customer.getPrimaryPhone();
        snapshot.put("primaryPhone",phone == null ? null : phone.length() >= 7 ? phone.substring(0,3)+"****"+phone.substring(phone.length()-4) : "****");
        return encode(snapshot);
    }
    private List<String> tags(String value) {
        if (value == null) return List.of();
        try { return json.readValue(value,new TypeReference<List<String>>() {}); }
        catch (Exception exception) { throw new IllegalStateException("Invalid stored customer tags",exception); }
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException("Cannot encode customer data",exception); }
    }
    public static String normalizeName(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFKC).replaceAll("[\\p{Z}\\s]+","").toLowerCase(Locale.ROOT);
    }
    public static String phone(String value) {
        String normalized = Normalizer.normalize(value,Normalizer.Form.NFKC).replaceAll("[()\\s-]","");
        if (normalized.startsWith("+86") && normalized.length() == 14) normalized = normalized.substring(3);
        if (!normalized.matches("\\+?[0-9]{5,20}")) throw new BizException(ErrorCode.VALIDATION_ERROR);
        return normalized;
    }
    private String text(String value) { String text = nullable(value); if (text == null) throw new BizException(ErrorCode.VALIDATION_ERROR); return text; }
    private String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private LocalDateTime now() { return LocalDateTime.now(businessClock); }
    private void changed(int count) { if (count != 1) throw new BizException(ErrorCode.CONFLICT); }
}
