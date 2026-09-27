package utp.siga.identity.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/** Local collectors run on this Windows host, on a separate loopback-only listener. */
@Configuration
@Profile("local")
public class LocalManagementSecurity {
  @Bean
  @Order(0)
  SecurityFilterChain localManagement(HttpSecurity http,
      @Value("${management.server.port}") int port) throws Exception {
    var ipv4 = new IpAddressMatcher("127.0.0.0/8");
    var ipv6 = new IpAddressMatcher("::1");
    http.securityMatcher(request -> request.getLocalPort() == port)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a
            .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/liveness",
                "/actuator/health/readiness", "/actuator/prometheus")
            .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                ipv4.matches(context.getRequest()) || ipv6.matches(context.getRequest())))
            .anyRequest().denyAll());
    return http.build();
  }
}
