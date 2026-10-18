package com.bishe.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.auth.security.JwtProperties;
import com.bishe.crm.module.auth.security.JwtTokenService;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    // Public deterministic fixture key, never used by the running application.
    private static final String TEST_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final AuthPrincipal USER = new AuthPrincipal(42L, "fixture", "Fixture", "SPECIALIST",
            8L, "Fixture team", true, 5L);

    private JwtProperties properties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(TEST_KEY);
        return properties;
    }

    private JwtTokenService service(Instant now) {
        return new JwtTokenService(properties(), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void signedTokenContainsIdentityAndHasConfiguredExpiry() {
        var issued = service(NOW).issue(USER);
        assertThat(issued.expiresIn()).isEqualTo(7200);
        assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(7200));
        var identity = service(NOW).parse(issued.value());
        assertThat(identity.userId()).isEqualTo(42);
        assertThat(identity.version()).isEqualTo(5);
    }

    @Test
    void expirationBoundaryRejectsTokenAtAndAfterExpiry() {
        String token = service(NOW).issue(USER).value();
        assertThat(service(NOW.plusSeconds(7199)).parse(token).userId()).isEqualTo(42);
        assertThatThrownBy(() -> service(NOW.plusSeconds(7200)).parse(token)).isInstanceOfAny(
                JwtException.class, IllegalArgumentException.class);
        assertThatThrownBy(() -> service(NOW.plusSeconds(7201)).parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperingAndWrongKeyAreRejected() {
        String token = service(NOW).issue(USER).value();
        String[] parts = token.split("\\.");
        char replacement = parts[1].charAt(5) == 'A' ? 'B' : 'A';
        parts[1] = parts[1].substring(0, 5) + replacement + parts[1].substring(6);
        assertThatThrownBy(() -> service(NOW).parse(String.join(".", parts))).isInstanceOf(JwtException.class);
        JwtProperties wrong = properties();
        wrong.setSecret("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        assertThatThrownBy(() -> new JwtTokenService(wrong, Clock.fixed(NOW, ZoneOffset.UTC)).parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void unsignedTokenAndUnexpectedAlgorithmAreRejected() {
        String unsigned = Jwts.builder().subject("42").compact();
        assertThatThrownBy(() -> service(NOW).parse(unsigned)).isInstanceOf(JwtException.class);
        var bigKey = Keys.hmacShaKeyFor(new byte[64]);
        String token = Jwts.builder().issuer("crm-backend").subject("42")
                .issuedAt(Date.from(NOW)).expiration(Date.from(NOW.plusSeconds(100)))
                .claim("ver", 5L).signWith(bigKey, Jwts.SIG.HS512).compact();
        assertThatThrownBy(() -> service(NOW).parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void wrongIssuerMissingExpiryAndFutureIssueTimeAreRejected() {
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_KEY));
        String wrongIssuer = Jwts.builder().issuer("another-app").subject("42")
                .expiration(Date.from(NOW.plusSeconds(100))).signWith(key, Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> service(NOW).parse(wrongIssuer)).isInstanceOf(JwtException.class);
        String missingExpiry = Jwts.builder().issuer("crm-backend").subject("42")
                .issuedAt(Date.from(NOW)).id("fixture").claim("ver", 5L)
                .signWith(key, Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> service(NOW).parse(missingExpiry)).isInstanceOf(IllegalArgumentException.class);
        String token = service(NOW.plusSeconds(1)).issue(USER).value();
        assertThatThrownBy(() -> service(NOW).parse(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidIdentityAndWeakKeyAreRejected() {
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_KEY));
        String token = Jwts.builder().issuer("crm-backend").subject("0").id("fixture")
                .issuedAt(Date.from(NOW)).expiration(Date.from(NOW.plusSeconds(100)))
                .claim("ver", -1L).signWith(key, Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> service(NOW).parse(token)).isInstanceOf(IllegalArgumentException.class);
        JwtProperties weak = properties();
        weak.setSecret("c2hvcnQ=");
        assertThatThrownBy(() -> new JwtTokenService(weak, Clock.fixed(NOW, ZoneOffset.UTC)))
                .isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class);
    }
}
