package com.bishe.crm.module.auth.service;

import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.auth.dto.ChangePasswordRequest;
import com.bishe.crm.module.auth.dto.LoginRequest;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.auth.security.AuthUsers;
import com.bishe.crm.module.auth.security.JwtTokenService;
import com.bishe.crm.module.auth.vo.CurrentUserVO;
import com.bishe.crm.module.auth.vo.LoginVO;
import com.bishe.crm.module.system.entity.SystemUser;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    // Valid BCrypt hash used only to keep nonexistent-account password checks comparable.
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private final SystemUserMapper users;
    private final PasswordEncoder passwords;
    private final JwtTokenService tokens;
    private final Clock businessClock;

    @Transactional
    public LoginVO login(LoginRequest request) {
        SystemUser user = users.findByUsername(request.getUsername());
        String hash = user == null ? DUMMY_HASH : user.getPasswordHash();
        boolean matches = request.getPassword().getBytes(StandardCharsets.UTF_8).length <= 72
                && passwords.matches(request.getPassword(), hash);
        if (!matches || !AuthUsers.usable(user)) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        if (users.recordLogin(user.getId(), user.getVersion(), hash, LocalDateTime.now(businessClock)) != 1) {
            throw new BizException(ErrorCode.CONFLICT);
        }
        user.setVersion(user.getVersion() + 1);
        AuthPrincipal principal = AuthPrincipal.from(user);
        var token = tokens.issue(principal);
        return new LoginVO(token.value(), "Bearer", token.expiresIn(), token.expiresAt(),
                CurrentUserVO.from(user));
    }

    public CurrentUserVO me(AuthPrincipal principal) {
        return CurrentUserVO.from(currentUser(principal));
    }

    @Transactional
    public void changePassword(AuthPrincipal principal, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        SystemUser user = currentUser(principal);
        if (request.getOldPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !passwords.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.PASSWORD_INCORRECT);
        }
        if (passwords.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.PASSWORD_UNCHANGED);
        }
        String newHash = passwords.encode(request.getNewPassword());
        if (users.changePassword(user.getId(), principal.version(), user.getPasswordHash(), newHash,
                LocalDateTime.now(businessClock)) != 1) {
            throw new BizException(ErrorCode.CONFLICT);
        }
        // Keep the password update and its successful audit in one database transaction.
        if (users.insertPasswordAudit(user.getId(), user.getTeamId(), LocalDateTime.now(businessClock)) != 1) {
            throw new IllegalStateException("Password audit was not persisted");
        }
    }

    @Transactional
    public void logout(AuthPrincipal principal) {
        if (users.revokeSession(principal.id(), principal.version(), LocalDateTime.now(businessClock)) != 1) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
    }

    private SystemUser currentUser(AuthPrincipal principal) {
        SystemUser user = users.findWithTeamById(principal.id());
        if (!AuthUsers.usable(user) || user.getVersion() != principal.version()) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        return user;
    }
}
