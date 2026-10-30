package utp.siga.identity.infrastructure.security;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import utp.siga.identity.application.port.out.AuthenticatedActorPort;
import utp.siga.identity.domain.exception.IdentityException;

@Component
public class SecurityContextActorAdapter implements AuthenticatedActorPort {
  @Override
  public UUID currentUserId() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      throw new IdentityException(401, "AUTH_INVALID", "Actor autenticado no disponible");
    }
    try {
      return UUID.fromString(jwt.getSubject());
    } catch (RuntimeException exception) {
      throw new IdentityException(401, "AUTH_INVALID", "Actor autenticado no válido");
    }
  }
}
