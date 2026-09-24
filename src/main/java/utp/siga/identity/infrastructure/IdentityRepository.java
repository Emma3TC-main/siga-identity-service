package utp.siga.identity.infrastructure;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import utp.siga.identity.application.Api;
import utp.siga.identity.domain.*;
import utp.siga.identity.presentation.CorrelationFilter;

@Repository
public class IdentityRepository implements IdentityStore {
  private final JdbcTemplate jdbc;

  public JdbcTemplate jdbc() {
    return jdbc;
  }

  public IdentityRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private final RowMapper<Account> mapper =
      (r, n) ->
          new Account(
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
            (r, n) ->
                new Api.Role(
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

  public void event(String type, UUID id) {
    var authentication =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    String actorId =
        authentication != null
                && authentication.getPrincipal()
                    instanceof org.springframework.security.oauth2.jwt.Jwt jwt
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
