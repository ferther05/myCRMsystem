/* 文件作用：签发和解析 JWT，验证签名、身份和有效期。 */
package com.bishe.crm.module.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** 签发和解析 JWT，验证签名、身份和有效期。 */
@Component
public class JwtTokenService {

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey key;
    private final JwtParser parser;

    /** 校验 JWT 配置并创建签名和解析工具。 */
    public JwtTokenService(JwtProperties properties, Clock businessClock) {
        this.properties = properties;
        this.clock = businessClock;
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret()));
        this.parser = Jwts.parser()
            .verifyWith(key)
            .requireIssuer(properties.getIssuer())
            .sig()
            .clear()
            .add(Jwts.SIG.HS256)
            .and()
            .clock(() -> Date.from(clock.instant()))
            .build();
    }

    /** 按用户及会话版本签发带到期时间的 JWT。 */
    public IssuedToken issue(AuthPrincipal user) {
        Instant issuedAt = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(properties.getTtlSeconds());
        String token = Jwts.builder()
            .issuer(properties.getIssuer())
            .subject(user.id().toString())
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .claim("ver", user.version())
            .claim("role", user.roleCode())
            .claim("team", user.teamId())
            .signWith(key, Jwts.SIG.HS256)
            .compact();
        return new IssuedToken(token, expiresAt, properties.getTtlSeconds());
    }

    /** 验证 JWT 签名、签发者、有效期及身份字段。 */
    public TokenIdentity parse(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        if (
            claims.getExpiration() == null ||
            claims.getIssuedAt() == null ||
            claims.getId() == null ||
            !claims.getExpiration().toInstant().isAfter(clock.instant()) ||
            claims.getIssuedAt().toInstant().isAfter(clock.instant()) ||
            !claims.getExpiration().after(claims.getIssuedAt())
        ) {
            throw new IllegalArgumentException("Invalid token lifetime");
        }
        long id = Long.parseLong(claims.getSubject());
        Long version = claims.get("ver", Long.class);
        if (id <= 0 || version == null || version < 0) {
            throw new IllegalArgumentException("Invalid token identity");
        }
        return new TokenIdentity(id, version);
    }

    /** JWT 解析后得到的用户及会话版本。 */
    public record TokenIdentity(long userId, long version) {}

    /** 签发的令牌及有效时长。 */
    public record IssuedToken(String value, Instant expiresAt, long expiresIn) {}
}
