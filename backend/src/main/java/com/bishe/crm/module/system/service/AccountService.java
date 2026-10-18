package com.bishe.crm.module.system.service;

import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.common.api.PageQuery;
import com.bishe.crm.common.api.PageResult;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.auth.vo.CurrentUserVO;
import com.bishe.crm.module.auth.vo.LoginVO;
import com.bishe.crm.module.system.dto.UserQuery;
import com.bishe.crm.module.system.dto.UserRequests;
import com.bishe.crm.module.system.entity.AccountApplication;
import com.bishe.crm.module.system.entity.SystemUser;
import com.bishe.crm.module.system.mapper.AccountMapper;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import com.bishe.crm.module.system.vo.TeamVO;
import com.bishe.crm.module.system.vo.UserVO;
import com.bishe.crm.module.system.vo.ApplicationVO;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountMapper accounts;
    private final SystemUserMapper users;
    private final PasswordEncoder passwords;
    private final JwtTokenService tokens;
    private final Clock businessClock;

    public PageResult<UserVO> list(AuthPrincipal actor, UserQuery query) {
        return PageResult.from(accounts.list(query.toPage(), DataScope.of(actor), query));
    }

    public UserVO detail(AuthPrincipal actor, Long id) {
        UserVO user = accounts.detail(id, DataScope.of(actor));
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND);
        return user;
    }

    public List<TeamVO> teams(AuthPrincipal actor) {
        return accounts.teams(DataScope.of(actor));
    }

    @Transactional
    public LoginVO profile(AuthPrincipal actor, UserRequests.Profile request) {
        lockActor(actor, false);
        changed(accounts.profile(actor.id(), actor.version(), name(request.realName()),
                nullable(request.phone()), nullable(request.email()), now()));
        audit(actor, actor.id(), "PROFILE_UPDATED", "用户修改本人姓名或联系方式，刷新当前会话");
        SystemUser updated = users.findWithTeamById(actor.id());
        var token = tokens.issue(AuthPrincipal.from(updated));
        return new LoginVO(token.value(), "Bearer", token.expiresIn(), token.expiresAt(), CurrentUserVO.from(updated));
    }

    @Transactional
    public UserVO create(AuthPrincipal actor, UserRequests.Create request) {
        lockActor(actor, true);
        team(request.roleCode(), request.teamId());
        SystemUser user = newUser(request.username(), request.realName(), request.phone(), request.email(),
                request.roleCode(), request.teamId(), "ACTIVE", request.initialPassword());
        changed(accounts.create(user));
        if ("MANAGER".equals(user.getRoleCode())) accounts.assignVacantManager(user.getTeamId(), user.getId());
        audit(actor, user.getId(), "ACCOUNT_CREATED", "管理员直接开通账号，首次登录需修改密码");
        return detail(actor, user.getId());
    }

    @Transactional
    public void access(AuthPrincipal actor, Long id, UserRequests.Access request) {
        lockActor(actor, true);
        if (actor.id().equals(id)) throw new BizException(ErrorCode.ACCOUNT_RULE);
        SystemUser target = lockTarget(id, request.version());
        if ("PENDING".equals(target.getAccountStatus())) throw new BizException(ErrorCode.ACCOUNT_RULE);
        boolean moving = !target.getRoleCode().equals(request.roleCode())
                || !Objects.equals(target.getTeamId(), request.teamId());
        if (moving && accounts.businessLinks(id) > 0) throw new BizException(ErrorCode.OWNERSHIP_PENDING);
        if ("ADMIN".equals(target.getRoleCode()) && "ACTIVE".equals(target.getAccountStatus())
                && (!"ADMIN".equals(request.roleCode()) || !"ACTIVE".equals(request.accountStatus()))
                && accounts.activeAdministrators() <= 1) throw new BizException(ErrorCode.ACCOUNT_RULE);
        team(request.roleCode(), request.teamId());
        changed(accounts.access(id, request.version(), request.roleCode(), request.teamId(), request.accountStatus(), now()));
        if (moving || !"ACTIVE".equals(request.accountStatus())) accounts.clearManager(id);
        if ("MANAGER".equals(request.roleCode()) && "ACTIVE".equals(request.accountStatus())) {
            accounts.assignVacantManager(request.teamId(), id);
        }
        audit(actor, id, "ACCOUNT_ACCESS_CHANGED", "角色 " + target.getRoleCode() + " → " + request.roleCode()
                + "；团队 " + target.getTeamId() + " → " + request.teamId() + "；状态 "
                + target.getAccountStatus() + " → " + request.accountStatus() + "；旧会话已撤销");
    }

    @Transactional
    public void reset(AuthPrincipal actor, Long id, UserRequests.Reset request) {
        lockActor(actor, true);
        if (actor.id().equals(id)) throw new BizException(ErrorCode.ACCOUNT_RULE);
        SystemUser user = lockTarget(id, request.version());
        if ("PENDING".equals(user.getAccountStatus())) throw new BizException(ErrorCode.ACCOUNT_RULE);
        if (passwords.matches(request.initialPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.PASSWORD_UNCHANGED);
        }
        changed(accounts.reset(id, request.version(), passwords.encode(request.initialPassword()), now()));
        audit(actor, id, "PASSWORD_RESET", "管理员重置密码，旧会话撤销，再次登录须改密");
    }

    public PageResult<ApplicationVO> applications(AuthPrincipal actor, PageQuery query) {
        managerOrAdmin(actor);
        return PageResult.from(accounts.applications(query.toPage(), DataScope.of(actor)));
    }

    @Transactional
    public Long apply(AuthPrincipal actor, UserRequests.Apply request) {
        if (!"MANAGER".equals(actor.roleCode())) throw new BizException(ErrorCode.FORBIDDEN);
        lockActor(actor, false);
        team("SPECIALIST", actor.teamId());
        // Reserve the unique username in the existing table. No usable initial password exists until approval.
        SystemUser pending = newUser(request.username(), request.realName(), request.phone(), request.email(),
                "SPECIALIST", actor.teamId(), "PENDING", UUID.randomUUID().toString());
        changed(accounts.create(pending));
        AccountApplication application = new AccountApplication();
        application.setBizId(pending.getId());
        application.setApplicantId(actor.id());
        application.setReason(name(request.reason()));
        changed(accounts.apply(application));
        audit(actor, pending.getId(), "ACCOUNT_APPLIED", "经理提交本团队专员账号申请，审批号 " + application.getId());
        return application.getId();
    }

    @Transactional
    public void review(AuthPrincipal actor, Long id, UserRequests.Review request) {
        lockActor(actor, true);
        AccountApplication application = accounts.lockApplication(id);
        if (application == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (!"PENDING".equals(application.getApprovalStatus()) || !request.version().equals(application.getVersion())) {
            throw new BizException(ErrorCode.CONFLICT);
        }
        if (request.approved()) {
            if (request.initialPassword() == null) throw new BizException(ErrorCode.VALIDATION_ERROR);
            team("SPECIALIST", application.getTeamId());
            changed(accounts.activate(application.getBizId(), passwords.encode(request.initialPassword()), now()));
        }
        changed(accounts.review(id, request.version(), request.approved() ? "APPROVED" : "REJECTED",
                actor.id(), now(), name(request.opinion())));
        audit(actor, application.getBizId(), request.approved() ? "ACCOUNT_APPROVED" : "ACCOUNT_REJECTED",
                "审批号 " + id + "；意见：" + name(request.opinion()));
    }

    @Transactional
    public void resubmit(AuthPrincipal actor, Long id, long version) {
        if (!"MANAGER".equals(actor.roleCode())) throw new BizException(ErrorCode.FORBIDDEN);
        lockActor(actor, false);
        AccountApplication application = accounts.lockApplication(id);
        if (application == null || !actor.id().equals(application.getApplicantId())
                || !actor.teamId().equals(application.getTeamId())) throw new BizException(ErrorCode.NOT_FOUND);
        changed(accounts.resubmit(id, version, now()));
        audit(actor, application.getBizId(), "ACCOUNT_RESUBMITTED", "经理重新提交被驳回的账号申请，审批号 " + id);
    }

    private void lockActor(AuthPrincipal actor, boolean adminRequired) {
        if (adminRequired) {
            if (!"ADMIN".equals(actor.roleCode())) throw new BizException(ErrorCode.FORBIDDEN);
            accounts.lockAdministrators();
        }
        SystemUser current = accounts.lockUser(actor.id());
        if (!AuthUsers.usable(current) || current.getVersion() != actor.version()
                || Boolean.TRUE.equals(current.getMustChangePassword())) throw new BizException(ErrorCode.TOKEN_INVALID);
    }

    private SystemUser lockTarget(Long id, Long version) {
        SystemUser target = accounts.lockUser(id);
        if (target == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (!version.equals(target.getVersion())) throw new BizException(ErrorCode.CONFLICT);
        return target;
    }

    private void team(String role, Long teamId) {
        if ("ADMIN".equals(role)) {
            if (teamId != null) throw new BizException(ErrorCode.ACCOUNT_RULE);
        } else if (teamId == null || accounts.lockEnabledTeam(teamId) == null) {
            throw new BizException(ErrorCode.ACCOUNT_RULE);
        }
    }

    private SystemUser newUser(String username, String realName, String phone, String email,
                               String role, Long team, String status, String password) {
        SystemUser user = new SystemUser();
        user.setUsername(username);
        user.setRealName(name(realName));
        user.setPhone(nullable(phone));
        user.setEmail(nullable(email));
        user.setRoleCode(role);
        user.setTeamId(team);
        user.setAccountStatus(status);
        user.setPasswordHash(passwords.encode(password));
        return user;
    }

    private void managerOrAdmin(AuthPrincipal actor) {
        if (!List.of("MANAGER", "ADMIN").contains(actor.roleCode())) throw new BizException(ErrorCode.FORBIDDEN);
    }

    private void audit(AuthPrincipal actor, Long id, String action, String reason) {
        changed(accounts.audit(id, action, actor.id(), actor.teamId(), now(), reason));
    }

    private void changed(int affected) {
        if (affected != 1) throw new BizException(ErrorCode.CONFLICT);
    }

    private String name(String value) {
        String text = value.trim();
        if (text.isEmpty()) throw new BizException(ErrorCode.VALIDATION_ERROR);
        return text;
    }

    private String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private LocalDateTime now() { return LocalDateTime.now(businessClock); }
}
