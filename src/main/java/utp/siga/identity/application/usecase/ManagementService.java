package utp.siga.identity.application.usecase;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utp.siga.identity.application.dto.Api;
import utp.siga.identity.application.port.in.IdentityManagementUseCase;
import utp.siga.identity.application.port.out.CredentialPort;
import utp.siga.identity.application.port.out.CryptoPort;
import utp.siga.identity.application.port.out.IdentityEventPort;
import utp.siga.identity.application.port.out.ManagementPersistencePort;
import utp.siga.identity.domain.exception.IdentityException;

@Service
public class ManagementService implements IdentityManagementUseCase {
  private final ManagementPersistencePort repo;
  private final IdentityEventPort events;
  private final CredentialPort passwords;
  private final CryptoPort crypto;

  public ManagementService(
      ManagementPersistencePort repo,
      IdentityEventPort events,
      CredentialPort passwords,
      CryptoPort crypto) {
    this.repo = repo;
    this.events = events;
    this.passwords = passwords;
    this.crypto = crypto;
  }

  private void validateRoles(Set<UUID> ids) {
    for (UUID id : ids) {
      if (!repo.role(id).active())
        throw new IdentityException(409, "ROLE_INACTIVE", "Rol inactivo");
    }
  }

  private void validatePermissions(Set<UUID> ids) {
    for (UUID id : ids) {
      if (!repo.permissionExists(id))
        throw new IdentityException(404, "PERMISSION_NOT_FOUND", "Permiso no encontrado");
    }
  }

  @Transactional
  public Api.User create(Api.CreateUser input) {
    UUID id = UUID.randomUUID();
    var roles = input.roleIds() == null ? Set.<UUID>of() : input.roleIds();
    validateRoles(roles);
    boolean mfa = Boolean.TRUE.equals(input.mfaEnabled());
    repo.createUser(
        id,
        input.username(),
        input.email(),
        passwords.encode(input.password()),
        mfa,
        mfa ? crypto.encrypt(crypto.newSecret()) : null);
    for (UUID role : roles)
      repo.addUserRole(id, role);
    events.append("UserCreated", id);
    return repo.user(id);
  }

  @Transactional
  public Api.User update(UUID id, Api.UpdateUser input) {
    var a = repo.accountForUpdate(id);
    if (Boolean.FALSE.equals(input.mfaEnabled()) && AuthService.privileged(repo.permissions(id)))
      throw new IdentityException(409, "MFA_REQUIRED", "Los usuarios privilegiados requieren MFA");
    if (input.email() != null)
      repo.updateEmail(id, input.email());
    if (input.active() != null)
      repo.updateActive(id, input.active());
    if (input.mfaEnabled() != null && input.mfaEnabled() != a.mfaEnabled()) {
      repo.updateMfa(
          id,
          input.mfaEnabled(),
          input.mfaEnabled() ? crypto.encrypt(crypto.newSecret()) : null);
    }
    if (input.active() != null || input.mfaEnabled() != null) repo.revokeUser(id);
    events.append(Boolean.FALSE.equals(input.active()) ? "UserDisabled" : "UserUpdated", id);
    return repo.user(id);
  }

  @Transactional
  public Api.User assignRoles(UUID id, Api.AssignRoles input) {
    repo.accountForUpdate(id);
    validateRoles(input.roleIds());
    repo.clearUserRoles(id);
    for (UUID role : input.roleIds())
      repo.addUserRole(id, role);
    repo.revokeUser(id);
    events.append("RoleChanged", id);
    return repo.user(id);
  }

  @Transactional
  public Api.Role createRole(Api.CreateRole input) {
    UUID id = UUID.randomUUID();
    var permissions = input.permissionIds() == null ? Set.<UUID>of() : input.permissionIds();
    validatePermissions(permissions);
    repo.createRole(id, input.code(), input.name());
    for (UUID p : permissions)
      repo.addRolePermission(id, p);
    events.append("RoleChanged", id);
    return repo.role(id);
  }

  @Transactional
  public Api.Role assignPermissions(UUID id, Api.AssignPermissions input) {
    if (repo.role(id).code().equals("ADMIN"))
      throw new IdentityException(409, "ROLE_PROTECTED", "El rol ADMIN es protegido");
    repo.lockRole(id);
    validatePermissions(input.permissionIds());
    repo.clearRolePermissions(id);
    for (UUID p : input.permissionIds())
      repo.addRolePermission(id, p);
    repo.revokeUsersByRole(id);
    events.append("RoleChanged", id);
    return repo.role(id);
  }
}
