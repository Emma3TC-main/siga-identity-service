package utp.siga.identity.infrastructure.security;

import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component("accessPolicy")
public class AccessPolicy {
  public boolean recent(Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof Jwt jwt)) return false;
    Number time = jwt.getClaim("auth_time");
    return time != null && Instant.now().getEpochSecond() - time.longValue() <= 300;
  }
}
