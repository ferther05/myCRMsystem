package com.bishe.crm.module.auth.security;

import com.bishe.crm.common.api.ApiResponse;
import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.module.system.entity.SystemUser;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService tokens;
    private final SystemUserMapper users;
    private final ObjectMapper json;

    public JwtAuthenticationFilter(JwtTokenService tokens, SystemUserMapper users, ObjectMapper json) {
        this.tokens = tokens;
        this.users = users;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return ("GET".equals(request.getMethod()) && "/api/health".equals(path))
                || ("POST".equals(request.getMethod()) && "/api/auth/login".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (header.length() > 4096 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    writeError(response, ErrorCode.TOKEN_INVALID);
                    return;
                }
                var identity = tokens.parse(header.substring(7));
                SystemUser user = users.findWithTeamById(identity.userId());
                if (!AuthUsers.usable(user) || user.getVersion() != identity.version()) {
                    writeError(response, ErrorCode.TOKEN_INVALID);
                    return;
                }
                AuthPrincipal principal = AuthPrincipal.from(user);
                var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.roleCode())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                if (principal.mustChangePassword() && !allowedBeforePasswordChange(request)) {
                    writeError(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
                    return;
                }
            } catch (JwtException | IllegalArgumentException exception) {
                writeError(response, ErrorCode.TOKEN_INVALID);
                return;
            } catch (DataAccessException exception) {
                writeError(response, ErrorCode.DEPENDENCY_UNAVAILABLE);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean allowedBeforePasswordChange(HttpServletRequest request) {
        String path = path(request);
        return ("GET".equals(request.getMethod()) && "/api/auth/me".equals(path))
                || ("POST".equals(request.getMethod())
                    && ("/api/auth/password".equals(path) || "/api/auth/logout".equals(path)));
    }

    private String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private void writeError(HttpServletResponse response, ErrorCode error) throws IOException {
        response.setStatus(error.status().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ApiResponse.failure(error));
    }
}
