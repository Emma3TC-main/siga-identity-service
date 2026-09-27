package utp.siga.identity.application.port.out;

import java.util.Set;
import java.util.UUID;
import utp.siga.identity.application.dto.Api;
import utp.siga.identity.domain.model.Account;

public interface ManagementPersistencePort {
  Api.Role role(UUID roleId);

  boolean permissionExists(UUID permissionId);

  void createUser(
      UUID userId,
      String username,
      String email,
      String passwordHash,
      boolean mfaEnabled,
      String encryptedSecret);

  void addUserRole(UUID userId, UUID roleId);

  Api.User user(UUID userId);

  Account accountForUpdate(UUID userId);

  Set<String> permissions(UUID userId);

  void updateEmail(UUID userId, String email);

  void updateActive(UUID userId, boolean active);

  void updateMfa(UUID userId, boolean enabled, String encryptedSecret);

  void revokeUser(UUID userId);

  void clearUserRoles(UUID userId);

  void createRole(UUID roleId, String code, String name);

  void addRolePermission(UUID roleId, UUID permissionId);

  void lockRole(UUID roleId);

  void clearRolePermissions(UUID roleId);

  void revokeUsersByRole(UUID roleId);
}
