package utp.siga.identity.application.port.out;

import java.util.UUID;

public interface AuthenticatedActorPort {
  UUID currentUserId();
}
