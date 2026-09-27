package utp.siga.identity.application.usecase;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import utp.siga.identity.application.dto.Api;
import utp.siga.identity.application.port.in.IdentityQueryUseCase;
import utp.siga.identity.application.port.out.IdentityQueryPort;

@Service
public class IdentityQueryService implements IdentityQueryUseCase {
  private final IdentityQueryPort queries;

  public IdentityQueryService(IdentityQueryPort queries) {
    this.queries = queries;
  }

  public Api.Page users(int page, int size) {
    return queries.users(page, size);
  }

  public Api.User user(UUID userId) {
    return queries.user(userId);
  }

  public List<Api.Role> roles() {
    return queries.roles();
  }

  public List<Map<String, Object>> permissions() {
    return queries.permissions();
  }
}
