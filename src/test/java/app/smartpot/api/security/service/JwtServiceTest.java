package app.smartpot.api.security.service;

import app.smartpot.api.security.config.SecurityConfiguration;
import app.smartpot.api.support.TestProperties;
import app.smartpot.api.users.model.entity.User;
import app.smartpot.api.users.model.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final SecurityConfiguration configuration = new SecurityConfiguration();
    private final JwtDecoder decoder = configuration.jwtDecoder(TestProperties.smartPot());

    private final User user = User.builder()
            .id("6718f0a1b2c3d4e5f6a7b8c9")
            .email("ana@example.com")
            .role(UserRole.USER)
            .build();

    @Test
    void issuesATokenThatTheDecoderAccepts() {
        JwtService service = new JwtService(configuration.jwtEncoder(TestProperties.smartPot()),
                Clock.systemUTC(), TestProperties.smartPot());

        JwtService.IssuedToken token = service.issue(user);
        Jwt jwt = decoder.decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo(user.getId());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("ana@example.com");
        assertThat(jwt.getExpiresAt()).isEqualTo(token.expiresAt());
    }

    @Test
    void rejectsExpiredTokens() {
        Clock past = Clock.fixed(Instant.now().minusSeconds(7200), ZoneOffset.UTC);
        JwtService service = new JwtService(configuration.jwtEncoder(TestProperties.smartPot()), past,
                TestProperties.smartPot());

        String token = service.issue(user).value();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
}
