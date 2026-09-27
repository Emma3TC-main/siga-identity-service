package utp.siga.identity.application.port.in;

import java.util.UUID;
import utp.siga.identity.application.dto.Api;

public interface IdentityManagementUseCase {
  Api.User create(Api.CreateUser input);

  Api.User update(UUID userId, Api.UpdateUser input);

  Api.User assignRoles(UUID userId, Api.AssignRoles input);

  Api.Role createRole(Api.CreateRole input);

  Api.Role assignPermissions(UUID roleId, Api.AssignPermissions input);
}
