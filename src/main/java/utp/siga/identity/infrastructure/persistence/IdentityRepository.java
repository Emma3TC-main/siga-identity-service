package utp.siga.identity.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import utp.siga.identity.application.dto.Api;
import utp.siga.identity.application.port.IdentityStore;
import utp.siga.identity.application.port.out.AuthPersistencePort;
import utp.siga.identity.application.port.out.IdentityEventPort;
import utp.siga.identity.application.port.out.IdentityQueryPort;
import utp.siga.identity.application.port.out.ManagementPersistencePort;
import utp.siga.identity.application.port.out.RefreshSession;
import utp.siga.identity.domain.exception.IdentityException;
import utp.siga.identity.domain.model.Account;
import utp.siga.identity.presentation.rest.CorrelationFilter;

@Repository
public class IdentityRepository
        implements IdentityStore,
                AuthPersistencePort,
                ManagementPersistencePort,
                IdentityEventPort,
                IdentityQueryPort {
    private final JdbcTemplate jdbc;

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public IdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<Account> mapper = (r, n) -> new Account(
            r.getObject("id", UUID.class),
            r.getString("username"),
            r.getString("email"),
            r.getString("password_hash"),
            r.getBoolean("active"),
            r.getBoolean("mfa_enabled"),
            r.getString("mfa_secret_encrypted"),
            r.getBoolean("mfa_enrolled"),
            (Long) r.getObject("last_totp_step"),
            r.getInt("failed_login_attempts"),
            r.getTimestamp("locked_until") == null
                    ? null
                    : r.getTimestamp("locked_until").toInstant());

    public Optional<Account> byUsername(String name) {
        return byUsernameForUpdate(name);
    }

    public Optional<Account> byUsernameForUpdate(String name) {
        return jdbc
                .query("SELECT * FROM iam.user_account WHERE username=? FOR UPDATE", mapper, name)
                .stream()
                .findFirst();
    }

    public Account account(UUID id, boolean lock) {
        return jdbc
                .query(
                        "SELECT * FROM iam.user_account WHERE id=?" + (lock ? " FOR UPDATE" : ""), mapper, id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IdentityException(404, "USER_NOT_FOUND", "Usuario no encontrado"));
    }

    public Set<String> permissions(UUID id) {
        return new TreeSet<>(
                jdbc.queryForList(
                        "SELECT DISTINCT p.code FROM iam.permission p JOIN iam.role_permission rp ON"
                                + " rp.permission_id=p.id JOIN iam.role r ON r.id=rp.role_id JOIN iam.user_role ur"
                                + " ON ur.role_id=r.id WHERE ur.user_id=? AND r.active",
                        String.class,
                        id));
    }

    public Account accountForUpdate(UUID id) {
        return account(id, true);
    }

    public void recordFailedLogin(UUID id, int attempts, Instant lockedUntil) {
        jdbc.update(
                "UPDATE iam.user_account SET failed_login_attempts=?,locked_until=?,updated_at=now()"
                        + " WHERE id=?",
                attempts,
                lockedUntil == null ? null : Timestamp.from(lockedUntil),
                id);
    }

    public void clearFailedLogin(UUID id) {
        jdbc.update(
                "UPDATE iam.user_account SET failed_login_attempts=0,locked_until=NULL WHERE id=?",
                id);
    }

    public void enableMfa(UUID id, String encryptedSecret) {
        jdbc.update(
                "UPDATE iam.user_account SET"
                        + " mfa_enabled=true,mfa_secret_encrypted=?,last_totp_step=NULL,updated_at=now()"
                        + " WHERE id=?",
                encryptedSecret,
                id);
    }

    public void confirmMfa(UUID id, long step) {
        jdbc.update(
                "UPDATE iam.user_account SET"
                        + " mfa_enrolled=true,mfa_enabled=true,last_totp_step=?,updated_at=now() WHERE"
                        + " id=?",
                step,
                id);
    }

    public void createRefreshSession(
            UUID userId,
            String tokenHash,
            UUID jti,
            UUID familyId,
            Instant expiresAt,
            Instant mfaAuthenticatedAt) {
        jdbc.update(
                "INSERT INTO"
                        + " iam.refresh_token(user_id,token_hash,jti,family_id,expires_at,mfa_authenticated_at)"
                        + " VALUES (?,?,?,?,?,?)",
                userId,
                tokenHash,
                jti,
                familyId,
                Timestamp.from(expiresAt),
                mfaAuthenticatedAt == null ? null : Timestamp.from(mfaAuthenticatedAt));
    }

    public Optional<UUID> refreshUserId(String tokenHash) {
        return jdbc
                .queryForList(
                        "SELECT user_id FROM iam.refresh_token WHERE token_hash=?",
                        UUID.class,
                        tokenHash)
                .stream()
                .findFirst();
    }

    public RefreshSession refreshForUpdate(String tokenHash) {
        var row = jdbc.queryForMap(
                "SELECT * FROM iam.refresh_token WHERE token_hash=? FOR UPDATE", tokenHash);
        Timestamp mfa = (Timestamp) row.get("mfa_authenticated_at");
        return new RefreshSession(
                (UUID) row.get("family_id"),
                row.get("revoked_at") != null,
                ((Timestamp) row.get("expires_at")).toInstant(),
                mfa == null ? null : mfa.toInstant());
    }

    public void revokeFamily(UUID familyId) {
        jdbc.update(
                "UPDATE iam.refresh_token SET revoked_at=coalesce(revoked_at,now()) WHERE"
                        + " family_id=?",
                familyId);
    }

    public void revokeUser(UUID userId) {
        revoke(userId);
    }

    public void rotateRefresh(String currentHash, String replacementHash) {
        jdbc.update(
                "UPDATE iam.refresh_token SET revoked_at=now(),replaced_by_jti=(SELECT jti FROM"
                        + " iam.refresh_token WHERE token_hash=?) WHERE token_hash=?",
                replacementHash,
                currentHash);
    }

    public void logout(UUID familyId, UUID userId) {
        jdbc.update(
                "UPDATE iam.refresh_token SET revoked_at=coalesce(revoked_at,now()) WHERE family_id=?"
                        + " AND user_id=?",
                familyId,
                userId);
    }

    public boolean permissionExists(UUID permissionId) {
        return !Boolean.FALSE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM iam.permission WHERE id=?)",
                        Boolean.class,
                        permissionId));
    }

    public void createUser(
            UUID id,
            String username,
            String email,
            String passwordHash,
            boolean mfaEnabled,
            String encryptedSecret) {
        jdbc.update(
                "INSERT INTO"
                        + " iam.user_account(id,username,email,password_hash,mfa_enabled,mfa_secret_encrypted)"
                        + " VALUES (?,?,?,?,?,?)",
                id,
                username,
                email,
                passwordHash,
                mfaEnabled,
                encryptedSecret);
    }

    public void addUserRole(UUID userId, UUID roleId) {
        jdbc.update("INSERT INTO iam.user_role(user_id,role_id) VALUES (?,?)", userId, roleId);
    }

    public void updateEmail(UUID userId, String email) {
        jdbc.update(
                "UPDATE iam.user_account SET email=?,updated_at=now(),version=version+1 WHERE id=?",
                email,
                userId);
    }

    public void updateActive(UUID userId, boolean active) {
        jdbc.update(
                "UPDATE iam.user_account SET active=?,updated_at=now(),version=version+1 WHERE id=?",
                active,
                userId);
    }

    public void updateMfa(UUID userId, boolean enabled, String encryptedSecret) {
        jdbc.update(
                "UPDATE iam.user_account SET"
                        + " mfa_enabled=?,mfa_enrolled=false,mfa_secret_encrypted=?,last_totp_step=NULL,updated_at=now(),version=version+1"
                        + " WHERE id=?",
                enabled,
                encryptedSecret,
                userId);
    }

    public void clearUserRoles(UUID userId) {
        jdbc.update("DELETE FROM iam.user_role WHERE user_id=?", userId);
    }

    public void createRole(UUID roleId, String code, String name) {
        jdbc.update("INSERT INTO iam.role(id,code,name) VALUES (?,?,?)", roleId, code, name);
    }

    public void addRolePermission(UUID roleId, UUID permissionId) {
        jdbc.update(
                "INSERT INTO iam.role_permission(role_id,permission_id) VALUES (?,?)",
                roleId,
                permissionId);
    }

    public void lockRole(UUID roleId) {
        jdbc.queryForList("SELECT id FROM iam.role WHERE id=? FOR UPDATE", UUID.class, roleId);
    }

    public void clearRolePermissions(UUID roleId) {
        jdbc.update("DELETE FROM iam.role_permission WHERE role_id=?", roleId);
    }

    public void revokeUsersByRole(UUID roleId) {
        jdbc.update(
                "UPDATE iam.refresh_token SET revoked_at=coalesce(revoked_at,now()) WHERE user_id IN"
                        + " (SELECT user_id FROM iam.user_role WHERE role_id=?)",
                roleId);
    }

    public Set<UUID> roleIds(UUID id) {
        return new HashSet<>(
                jdbc.queryForList("SELECT role_id FROM iam.user_role WHERE user_id=?", UUID.class, id));
    }

    public Api.User user(UUID id) {
        var a = account(id, false);
        return new Api.User(id, a.username(), a.email(), a.active(), a.mfaEnabled(), roleIds(id));
    }

    public Api.Role role(UUID id) {
        return jdbc
                .query(
                        "SELECT * FROM iam.role WHERE id=?",
                        (r, n) -> new Api.Role(
                                id,
                                r.getString("code"),
                                r.getString("name"),
                                r.getBoolean("active"),
                                new HashSet<>(
                                        jdbc.queryForList(
                                                "SELECT permission_id FROM iam.role_permission WHERE role_id=?",
                                                UUID.class,
                                                id))),
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IdentityException(404, "ROLE_NOT_FOUND", "Rol no encontrado"));
    }

    public List<Api.Role> roles() {
        return jdbc.queryForList("SELECT id FROM iam.role ORDER BY code", UUID.class).stream()
                .map(this::role)
                .toList();
    }

    public Api.Page users(int page, int size) {
        return new Api.Page(
                jdbc
                        .queryForList(
                                "SELECT id FROM iam.user_account ORDER BY username LIMIT ? OFFSET ?",
                                UUID.class,
                                size,
                                (long) page * size)
                        .stream()
                        .map(this::user)
                        .toList(),
                page,
                size,
                jdbc.queryForObject("SELECT count(*) FROM iam.user_account", Long.class));
    }

    public List<Map<String, Object>> permissions() {
        return jdbc.queryForList("SELECT id,code,description FROM iam.permission ORDER BY code");
    }

    public void event(String type, UUID id) {
        append(type, id);
    }

    public void append(String type, UUID id) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication();
        String actorId = authentication != null
                && authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt
                        ? jwt.getSubject()
                        : null;
        jdbc.update(
                "INSERT INTO"
                        + " iam.outbox_event(aggregate_type,aggregate_id,event_type,correlation_id,payload)"
                        + " VALUES ('Identity',?,?,?,jsonb_build_object('id',?::text,'actorId',?::text))",
                id,
                type,
                CorrelationFilter.current(),
                id.toString(),
                actorId);
    }

    public void revoke(UUID id) {
        jdbc.update(
                "UPDATE iam.refresh_token SET revoked_at=coalesce(revoked_at,now()) WHERE user_id=?", id);
    }
}
