package utp.siga.identity.application.port.in;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import utp.siga.identity.application.dto.Api;

public interface IdentityQueryUseCase {
  Api.Page users(int page, int size);

  Api.User user(UUID userId);

  List<Api.Role> roles();

  List<Map<String, Object>> permissions();
}
