package utp.siga.identity.application;

import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utp.siga.identity.domain.*;
import utp.siga.identity.infrastructure.*;

@Service
public class ManagementService {
  private final IdentityRepository repo;
  private final PasswordEncoder passwords;
  private final Crypto crypto;

  public ManagementService(IdentityRepository repo, PasswordEncoder passwords, Crypto crypto) {
    this.repo = repo;
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
      if (Boolean.FALSE.equals(
          repo.jdbc()
              .queryForObject(
                  "SELECT EXISTS(SELECT 1 FROM iam.permission WHERE id=?)", Boolean.class, id)))
        throw new IdentityException(404, "PERMISSION_NOT_FOUND", "Permiso no encontrado");
    }
  }

  @Transactional
  public Api.User create(Api.CreateUser input) {
    UUID id = UUID.randomUUID();
    var roles = input.roleIds() == null ? Set.<UUID>of() : input.roleIds();
    validateRoles(roles);
    boolean mfa = Boolean.TRUE.equals(input.mfaEnabled());
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " iam.user_account(id,username,email,password_hash,mfa_enabled,mfa_secret_encrypted)"
                + " VALUES (?,?,?,?,?,?)",
            id,
            input.username(),
            input.email(),
            passwords.encode(input.password()),
            mfa,
            mfa ? crypto.encrypt(crypto.newSecret()) : null);
    for (UUID role : roles)
      repo.jdbc().update("INSERT INTO iam.user_role(user_id,role_id) VALUES (?,?)", id, role);
    repo.event("UserCreated", id);
    return repo.user(id);
  }

  @Transactional
  public Api.User update(UUID id, Api.UpdateUser input) {
    var a = repo.account(id, true);
    if (Boolean.FALSE.equals(input.mfaEnabled()) && AuthService.privileged(repo.permissions(id)))
      throw new IdentityException(409, "MFA_REQUIRED", "Los usuarios privilegiados requieren MFA");
    if (input.email() != null)
      repo.jdbc()
          .update(
              "UPDATE iam.user_account SET email=?,updated_at=now(),version=version+1 WHERE id=?",
              input.email(),
              id);
    if (input.active() != null)
      repo.jdbc()
          .update(
              "UPDATE iam.user_account SET active=?,updated_at=now(),version=version+1 WHERE id=?",
              input.active(),
              id);
    if (input.mfaEnabled() != null && input.mfaEnabled() != a.mfaEnabled()) {
      repo.jdbc()
          .update(
              "UPDATE iam.user_account SET"
                  + " mfa_enabled=?,mfa_enrolled=false,mfa_secret_encrypted=?,last_totp_step=NULL,updated_at=now(),version=version+1"
                  + " WHERE id=?",
              input.mfaEnabled(),
              input.mfaEnabled() ? crypto.encrypt(crypto.newSecret()) : null,
              id);
    }
    if (input.active() != null || input.mfaEnabled() != null) repo.revoke(id);
    repo.event(Boolean.FALSE.equals(input.active()) ? "UserDisabled" : "UserUpdated", id);
    return repo.user(id);
  }

  @Transactional
  public Api.User assignRoles(UUID id, Api.AssignRoles input) {
    repo.account(id, true);
    validateRoles(input.roleIds());
    repo.jdbc().update("DELETE FROM iam.user_role WHERE user_id=?", id);
    for (UUID role : input.roleIds())
      repo.jdbc().update("INSERT INTO iam.user_role(user_id,role_id) VALUES (?,?)", id, role);
    repo.revoke(id);
    repo.event("RoleChanged", id);
    return repo.user(id);
  }

  @Transactional
  public Api.Role createRole(Api.CreateRole input) {
    UUID id = UUID.randomUUID();
    var permissions = input.permissionIds() == null ? Set.<UUID>of() : input.permissionIds();
    validatePermissions(permissions);
    repo.jdbc()
        .update(
            "INSERT INTO iam.role(id,code,name) VALUES (?,?,?)", id, input.code(), input.name());
    for (UUID p : permissions)
      repo.jdbc()
          .update("INSERT INTO iam.role_permission(role_id,permission_id) VALUES (?,?)", id, p);
    repo.event("RoleChanged", id);
    return repo.role(id);
  }

  @Transactional
  public Api.Role assignPermissions(UUID id, Api.AssignPermissions input) {
    if (repo.role(id).code().equals("ADMIN"))
      throw new IdentityException(409, "ROLE_PROTECTED", "El rol ADMIN es protegido");
    repo.jdbc().queryForList("SELECT id FROM iam.role WHERE id=? FOR UPDATE", UUID.class, id);
    validatePermissions(input.permissionIds());
    repo.jdbc().update("DELETE FROM iam.role_permission WHERE role_id=?", id);
    for (UUID p : input.permissionIds())
      repo.jdbc()
          .update("INSERT INTO iam.role_permission(role_id,permission_id) VALUES (?,?)", id, p);
    repo.jdbc()
        .update(
            "UPDATE iam.refresh_token SET revoked_at=coalesce(revoked_at,now()) WHERE user_id IN"
                + " (SELECT user_id FROM iam.user_role WHERE role_id=?)",
            id);
    repo.event("RoleChanged", id);
    return repo.role(id);
  }
}
