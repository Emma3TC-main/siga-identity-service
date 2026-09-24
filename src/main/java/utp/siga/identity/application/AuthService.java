package utp.siga.identity.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utp.siga.identity.domain.*;
import utp.siga.identity.infrastructure.*;

@Service
public class AuthService {
  private final IdentityRepository repo;
  private final PasswordEncoder passwords;
  private final Crypto crypto;
  private final TokenService tokens;
  private final ChallengeStore challenges;
  private final int max;
  private final long lockSeconds;
  private final String dummy;

  public AuthService(
      IdentityRepository repo,
      PasswordEncoder passwords,
      Crypto crypto,
      TokenService tokens,
      ChallengeStore challenges,
      @Value("${iam.max-attempts}") int max,
      @Value("${iam.lock-seconds}") long lockSeconds) {
    this.repo = repo;
    this.passwords = passwords;
    this.crypto = crypto;
    this.tokens = tokens;
    this.challenges = challenges;
    this.max = max;
    this.lockSeconds = lockSeconds;
    dummy = passwords.encode(crypto.randomToken());
  }

  public static boolean privileged(Set<String> permissions) {
    return permissions.stream()
        .anyMatch(
            Set.of("USER_MANAGE", "ROLE_MANAGE", "MOVEMENT_AUTHORIZE", "INVENTORY_ADJUST")
                ::contains);
  }

  @Transactional(noRollbackFor = IdentityException.class)
  public Api.LoginResult login(Api.Login input, String ip) {
    challenges.limit(ip);
    var found = repo.byUsername(input.username());
    if (found.isEmpty()) {
      passwords.matches(input.password(), dummy);
      throw IdentityException.unauthorized();
    }
    var a = found.get();
    if (a.lockedUntil() != null && a.lockedUntil().isAfter(Instant.now()))
      throw new IdentityException(429, "AUTH_LOCKED", "Demasiados intentos; reintenta más tarde");
    if (!a.active() || !passwords.matches(input.password(), a.passwordHash())) {
      int attempts = a.lockedUntil() != null ? 1 : a.failures() + 1;
      repo.jdbc()
          .update(
              "UPDATE iam.user_account SET failed_login_attempts=?,locked_until=?,updated_at=now()"
                  + " WHERE id=?",
              attempts,
              attempts >= max ? Timestamp.from(Instant.now().plusSeconds(lockSeconds)) : null,
              a.id());
      throw IdentityException.unauthorized();
    }
    repo.jdbc()
        .update(
            "UPDATE iam.user_account SET failed_login_attempts=0,locked_until=NULL WHERE id=?",
            a.id());
    if (a.mfaEnabled() || privileged(repo.permissions(a.id())))
      return new Api.LoginResult(true, challenges.create(a.id()), null);
    return new Api.LoginResult(false, null, issue(a, UUID.randomUUID(), null));
  }

  @Transactional
  public Api.EnrollmentResult enroll(Api.Enrollment input) {
    var id = challenges.user(input.challengeId());
    var a = repo.account(id, true);
    if (!a.active() || a.enrolled())
      throw new IdentityException(
          409, "MFA_ALREADY_ENROLLED", "No es posible iniciar el enrolamiento");
    String secret = crypto.newSecret();
    repo.jdbc()
        .update(
            "UPDATE iam.user_account SET"
                + " mfa_enabled=true,mfa_secret_encrypted=?,last_totp_step=NULL,updated_at=now()"
                + " WHERE id=?",
            crypto.encrypt(secret),
            id);
    String label = URLEncoder.encode("SIGA:" + a.username(), StandardCharsets.UTF_8);
    return new Api.EnrollmentResult(
        secret,
        "otpauth://totp/"
            + label
            + "?secret="
            + secret
            + "&issuer=SIGA&algorithm=SHA1&digits=6&period=30");
  }

  @Transactional(noRollbackFor = IdentityException.class)
  public Api.Tokens verify(Api.Mfa input) {
    challenges.attempt(input.challengeId());
    UUID id = challenges.user(input.challengeId());
    var a = repo.account(id, true);
    challenges.user(input.challengeId());
    if (!a.active() || a.encryptedSecret() == null) throw IdentityException.unauthorized();
    Long step =
        Crypto.verify(
            crypto.decrypt(a.encryptedSecret()),
            input.otp(),
            Instant.now().getEpochSecond(),
            a.lastStep());
    if (step == null) throw IdentityException.unauthorized();
    repo.jdbc()
        .update(
            "UPDATE iam.user_account SET"
                + " mfa_enrolled=true,mfa_enabled=true,last_totp_step=?,updated_at=now() WHERE"
                + " id=?",
            step,
            id);
    challenges.consume(input.challengeId());
    return issue(a, UUID.randomUUID(), Instant.now().getEpochSecond());
  }

  private Api.Tokens issue(Account a, UUID family, Long mfaTime) {
    String value = crypto.randomToken();
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " iam.refresh_token(user_id,token_hash,jti,family_id,expires_at,mfa_authenticated_at)"
                + " VALUES (?,?,?,?,?,?)",
            a.id(),
            Crypto.hash(value),
            UUID.randomUUID(),
            family,
            Timestamp.from(Instant.now().plusSeconds(tokens.refreshSeconds)),
            mfaTime == null ? null : Timestamp.from(Instant.ofEpochSecond(mfaTime)));
    return new Api.Tokens(
        tokens.access(a, repo.permissions(a.id()), family, mfaTime),
        value,
        tokens.accessSeconds,
        "Bearer");
  }

  @Transactional(noRollbackFor = IdentityException.class)
  public Api.Tokens refresh(Api.Refresh input) {
    String hash = Crypto.hash(input.refreshToken());
    var ids =
        repo.jdbc()
            .queryForList(
                "SELECT user_id FROM iam.refresh_token WHERE token_hash=?", UUID.class, hash);
    if (ids.isEmpty()) throw IdentityException.unauthorized();
    var a = repo.account(ids.getFirst(), true);
    var row =
        repo.jdbc()
            .queryForMap("SELECT * FROM iam.refresh_token WHERE token_hash=? FOR UPDATE", hash);
    UUID family = (UUID) row.get("family_id");
    if (row.get("revoked_at") != null) {
      repo.jdbc()
          .update(
              "UPDATE iam.refresh_token SET revoked_at=coalesce(revoked_at,now()) WHERE"
                  + " family_id=?",
              family);
      throw new IdentityException(
          401, "AUTH_REFRESH_REUSED", "Sesión revocada por reutilización de token");
    }
    if (!a.active() || ((Timestamp) row.get("expires_at")).toInstant().isBefore(Instant.now()))
      throw IdentityException.unauthorized();
    Timestamp mfa = (Timestamp) row.get("mfa_authenticated_at");
    if ((a.mfaEnabled() || privileged(repo.permissions(a.id()))) && mfa == null) {
      repo.revoke(a.id());
      throw IdentityException.unauthorized();
    }
    var issued = issue(a, family, mfa == null ? null : mfa.toInstant().getEpochSecond());
    repo.jdbc()
        .update(
            "UPDATE iam.refresh_token SET revoked_at=now(),replaced_by_jti=(SELECT jti FROM"
                + " iam.refresh_token WHERE token_hash=?) WHERE token_hash=?",
            Crypto.hash(issued.refreshToken()),
            hash);
    return issued;
  }

  @Transactional
  public void logout(Jwt jwt) {
    repo.account(UUID.fromString(jwt.getSubject()), true);
    repo.jdbc()
        .update(
            "UPDATE iam.refresh_token SET revoked_at=coalesce(revoked_at,now()) WHERE family_id=?"
                + " AND user_id=?",
            UUID.fromString(jwt.getClaimAsString("sid")),
            UUID.fromString(jwt.getSubject()));
  }
}
