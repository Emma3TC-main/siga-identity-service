package utp.siga.identity.application.port.out;

import utp.siga.identity.application.event.IdentityAuditEvent;

public interface IdentityEventPort {
  void append(IdentityAuditEvent event);
}
