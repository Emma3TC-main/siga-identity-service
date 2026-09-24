package utp.siga.identity.infrastructure;

import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import utp.siga.identity.application.AuthService;
import utp.siga.identity.presentation.Problems;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
  @Bean
  PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  @Bean
  JwtDecoder decoder(TokenService tokens) {
    var decoder = NimbusJwtDecoder.withPublicKey(tokens.publicKey).build();
    OAuth2TokenValidator<Jwt> audience =
        jwt ->
            jwt.getAudience().contains(tokens.audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(tokens.issuer), audience));
    return decoder;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, IdentityRepository repo) throws Exception {
    http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.authorizeHttpRequests(
        a ->
            a.requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/mfa/verify",
                    "/api/v1/auth/mfa/enroll",
                    "/api/v1/auth/refresh",
                    "/.well-known/jwks.json",
                    "/actuator/health")
                .permitAll()
                .requestMatchers("/api/v1/**")
                .authenticated()
                .anyRequest()
                .denyAll());
    http.exceptionHandling(
        e ->
            e.authenticationEntryPoint(
                    (req, res, x) -> Problems.write(req, res, 401, "AUTH_INVALID"))
                .accessDeniedHandler(
                    (req, res, x) -> Problems.write(req, res, 403, "AUTH_FORBIDDEN")));
    http.oauth2ResourceServer(
        o ->
            o.authenticationEntryPoint(
                    (req, res, x) -> Problems.write(req, res, 401, "AUTH_INVALID"))
                .accessDeniedHandler(
                    (req, res, x) -> Problems.write(req, res, 403, "AUTH_FORBIDDEN"))
                .jwt(
                    j ->
                        j.jwtAuthenticationConverter(
                            jwt -> {
                              try {
                                UUID id = UUID.fromString(jwt.getSubject());
                                var account = repo.account(id, false);
                                UUID family = UUID.fromString(jwt.getClaimAsString("sid"));
                                boolean live =
                                    Boolean.TRUE.equals(
                                        repo.jdbc()
                                            .queryForObject(
                                                "SELECT EXISTS(SELECT 1 FROM iam.refresh_token"
                                                    + " WHERE family_id=? AND user_id=? AND"
                                                    + " revoked_at IS NULL AND expires_at>now())",
                                                Boolean.class,
                                                family,
                                                id));
                                var permissions = repo.permissions(id);
                                boolean otp =
                                    Optional.ofNullable(jwt.getClaimAsStringList("amr"))
                                        .orElse(List.of())
                                        .contains("otp");
                                if (!account.active()
                                    || !live
                                    || ((account.mfaEnabled()
                                            || AuthService.privileged(permissions))
                                        && !otp)) throw new IllegalArgumentException();
                                return new JwtAuthenticationToken(
                                    jwt,
                                    permissions.stream().map(SimpleGrantedAuthority::new).toList());
                              } catch (Exception e) {
                                throw new OAuth2AuthenticationException(
                                    new OAuth2Error("invalid_token"));
                              }
                            })));
    return http.build();
  }
}
