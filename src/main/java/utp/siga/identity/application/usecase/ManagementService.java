package utp.siga.identity.application.usecase;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utp.siga.identity.application.dto.Api;
import utp.siga.identity.application.event.IdentityAuditEvent;
import utp.siga.identity.application.port.in.IdentityManagementUseCase;
import utp.siga.identity.application.port.out.AuthenticatedActorPort;
import utp.siga.identity.application.port.out.CredentialPort;
import utp.siga.identity.application.port.out.CryptoPort;
import utp.siga.identity.application.port.out.IdentityEventPort;
import utp.siga.identity.application.port.out.ManagementPersistencePort;
import utp.siga.identity.domain.exception.IdentityException;

@Service
public class ManagementService implements IdentityManagementUseCase {
  private final ManagementPersistencePort repo;
  private final IdentityEventPort events;
  private final AuthenticatedActorPort actor;
  private final CredentialPort passwords;
  private final CryptoPort crypto;

  public ManagementService(
      ManagementPersistencePort repo,
      IdentityEventPort events,
      AuthenticatedActorPort actor,
      CredentialPort passwords,
      CryptoPort crypto) {
    this.repo = repo;
    this.events = events;
    this.actor = actor;
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
    var after = repo.user(id);
    events.append(userEvent("UserCreated", id, null, after));
    return after;
  }

  @Transactional
  public Api.User update(UUID id, Api.UpdateUser input) {
    repo.accountForUpdate(id);
    var before = repo.user(id);
    if (Boolean.FALSE.equals(input.mfaEnabled()) && AuthService.privileged(repo.permissions(id)))
      throw new IdentityException(409, "MFA_REQUIRED", "Los usuarios privilegiados requieren MFA");
    boolean emailChanged = input.email() != null && !input.email().equals(before.email());
    boolean activeChanged = input.active() != null && input.active() != before.active();
    boolean mfaChanged = input.mfaEnabled() != null && input.mfaEnabled() != before.mfaEnabled();
    if (!emailChanged && !activeChanged && !mfaChanged) return before;
    if (emailChanged)
      repo.updateEmail(id, input.email());
    if (activeChanged)
      repo.updateActive(id, input.active());
    if (mfaChanged) {
      repo.updateMfa(
          id,
          input.mfaEnabled(),
          input.mfaEnabled() ? crypto.encrypt(crypto.newSecret()) : null);
    }
    if (activeChanged || mfaChanged) repo.revokeUser(id);
    var after = repo.user(id);
    String eventType = before.active() && !after.active() ? "UserDisabled" : "UserUpdated";
    events.append(userEvent(eventType, id, before, after));
    return after;
  }

  @Transactional
  public Api.User assignRoles(UUID id, Api.AssignRoles input) {
    repo.accountForUpdate(id);
    var before = repo.user(id);
    validateRoles(input.roleIds());
    if (new HashSet<>(before.roleIds()).equals(input.roleIds())) return before;
    repo.clearUserRoles(id);
    for (UUID role : input.roleIds())
      repo.addUserRole(id, role);
    repo.revokeUser(id);
    var after = repo.user(id);
    events.append(userEvent("UserRolesChanged", id, before, after));
    return after;
  }

  @Transactional
  public Api.Role createRole(Api.CreateRole input) {
    UUID id = UUID.randomUUID();
    var permissions = input.permissionIds() == null ? Set.<UUID>of() : input.permissionIds();
    validatePermissions(permissions);
    repo.createRole(id, input.code(), input.name());
    for (UUID p : permissions)
      repo.addRolePermission(id, p);
    var after = repo.role(id);
    events.append(roleEvent("RoleCreated", id, null, after));
    return after;
  }

  @Transactional
  public Api.Role assignPermissions(UUID id, Api.AssignPermissions input) {
    repo.lockRole(id);
    var before = repo.role(id);
    if (before.code().equals("ADMIN"))
      throw new IdentityException(409, "ROLE_PROTECTED", "El rol ADMIN es protegido");
    validatePermissions(input.permissionIds());
    if (new HashSet<>(before.permissionIds()).equals(input.permissionIds())) return before;
    repo.clearRolePermissions(id);
    for (UUID p : input.permissionIds())
      repo.addRolePermission(id, p);
    repo.revokeUsersByRole(id);
    var after = repo.role(id);
    events.append(roleEvent("RolePermissionsChanged", id, before, after));
    return after;
  }

  private IdentityAuditEvent userEvent(
      String eventType, UUID id, Api.User before, Api.User after) {
    return new IdentityAuditEvent(
        2,
        eventType,
        "User",
        id,
        new IdentityAuditEvent.UserPayload(id, actor.currentUserId(), before, after));
  }

  private IdentityAuditEvent roleEvent(
      String eventType, UUID id, Api.Role before, Api.Role after) {
    return new IdentityAuditEvent(
        2,
        eventType,
        "Role",
        id,
        new IdentityAuditEvent.RolePayload(id, actor.currentUserId(), before, after));
  }
}
