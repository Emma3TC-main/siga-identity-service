package utp.siga.identity.application.port.out;

import java.util.Set;
import java.util.UUID;
import utp.siga.identity.domain.model.Account;

public interface TokenIssuerPort {
  String access(Account account, Set<String> permissions, UUID familyId, Long mfaTime);

  long accessSeconds();

  long refreshSeconds();
}
