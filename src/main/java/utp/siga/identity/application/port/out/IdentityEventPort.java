package utp.siga.identity.application.port.out;

import java.util.UUID;

public interface IdentityEventPort {
  void append(String eventType, UUID aggregateId);
}
