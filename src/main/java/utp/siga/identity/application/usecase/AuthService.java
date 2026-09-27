package utp.siga.identity.application.usecase;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utp.siga.identity.application.dto.Api;
import utp.siga.identity.application.port.in.AuthenticationUseCase;
import utp.siga.identity.application.port.out.AuthPersistencePort;
import utp.siga.identity.application.port.out.ChallengePort;
import utp.siga.identity.application.port.out.CredentialPort;
import utp.siga.identity.application.port.out.CryptoPort;
import utp.siga.identity.application.port.out.TokenIssuerPort;
import utp.siga.identity.domain.exception.IdentityException;
import utp.siga.identity.domain.model.Account;

@Service
public class AuthService implements AuthenticationUseCase {
    private final AuthPersistencePort repo;
    private final CredentialPort passwords;
    private final CryptoPort crypto;
    private final TokenIssuerPort tokens;
    private final ChallengePort challenges;
    private final int max;
    private final long lockSeconds;
    private final String dummy;

    public AuthService(
            AuthPersistencePort repo,
            CredentialPort passwords,
            CryptoPort crypto,
            TokenIssuerPort tokens,
            ChallengePort challenges,
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
                        Set.of("USER_MANAGE", "ROLE_MANAGE", "MOVEMENT_AUTHORIZE", "INVENTORY_ADJUST")::contains);
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public Api.LoginResult login(Api.Login input, String ip) {
        challenges.limit(ip);
        var found = repo.byUsernameForUpdate(input.username());
        if (found.isEmpty()) {
            passwords.matches(input.password(), dummy);
            throw IdentityException.unauthorized();
        }
        var a = found.get();
        if (a.lockedUntil() != null && a.lockedUntil().isAfter(Instant.now()))
            throw new IdentityException(429, "AUTH_LOCKED", "Demasiados intentos; reintenta más tarde");
        if (!a.active() || !passwords.matches(input.password(), a.passwordHash())) {
            int attempts = a.lockedUntil() != null ? 1 : a.failures() + 1;
            repo.recordFailedLogin(
                    a.id(),
                    attempts,
                    attempts >= max ? Instant.now().plusSeconds(lockSeconds) : null);
            throw IdentityException.unauthorized();
        }
        repo.clearFailedLogin(a.id());
        if (a.mfaEnabled() || privileged(repo.permissions(a.id())))
            return new Api.LoginResult(true, challenges.create(a.id()), null);
        return new Api.LoginResult(false, null, issue(a, UUID.randomUUID(), null));
    }

    @Transactional
    public Api.EnrollmentResult enroll(Api.Enrollment input) {
        var id = challenges.user(input.challengeId());
        var a = repo.accountForUpdate(id);
        if (!a.active() || a.enrolled())
            throw new IdentityException(
                    409, "MFA_ALREADY_ENROLLED", "No es posible iniciar el enrolamiento");
        String secret = crypto.newSecret();
        repo.enableMfa(id, crypto.encrypt(secret));
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
        var a = repo.accountForUpdate(id);
        challenges.user(input.challengeId());
        if (!a.active() || a.encryptedSecret() == null)
            throw IdentityException.unauthorized();
        Long step = crypto.verifyTotp(
                crypto.decrypt(a.encryptedSecret()),
                input.otp(),
                Instant.now().getEpochSecond(),
                a.lastStep());
        if (step == null)
            throw IdentityException.unauthorized();
        repo.confirmMfa(id, step);
        challenges.consume(input.challengeId());
        return issue(a, UUID.randomUUID(), Instant.now().getEpochSecond());
    }

    private Api.Tokens issue(Account a, UUID family, Long mfaTime) {
        String value = crypto.randomToken();
        repo.createRefreshSession(
                a.id(),
                crypto.hashValue(value),
                UUID.randomUUID(),
                family,
                Instant.now().plusSeconds(tokens.refreshSeconds()),
                mfaTime == null ? null : Instant.ofEpochSecond(mfaTime));
        return new Api.Tokens(
                tokens.access(a, repo.permissions(a.id()), family, mfaTime),
                value,
                tokens.accessSeconds(),
                "Bearer");
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public Api.Tokens refresh(Api.Refresh input) {
        String hash = crypto.hashValue(input.refreshToken());
        var userId = repo.refreshUserId(hash);
        if (userId.isEmpty())
            throw IdentityException.unauthorized();
        var a = repo.accountForUpdate(userId.get());
        var refresh = repo.refreshForUpdate(hash);
        UUID family = refresh.familyId();
        if (refresh.revoked()) {
            repo.revokeFamily(family);
            throw new IdentityException(
                    401, "AUTH_REFRESH_REUSED", "Sesión revocada por reutilización de token");
        }
        if (!a.active() || refresh.expiresAt().isBefore(Instant.now()))
            throw IdentityException.unauthorized();
        Instant mfa = refresh.mfaAuthenticatedAt();
        if ((a.mfaEnabled() || privileged(repo.permissions(a.id()))) && mfa == null) {
            repo.revokeUser(a.id());
            throw IdentityException.unauthorized();
        }
        var issued = issue(a, family, mfa == null ? null : mfa.getEpochSecond());
        repo.rotateRefresh(hash, crypto.hashValue(issued.refreshToken()));
        return issued;
    }

    @Transactional
    public void logout(UUID userId, UUID familyId) {
        repo.accountForUpdate(userId);
        repo.logout(familyId, userId);
    }
}
