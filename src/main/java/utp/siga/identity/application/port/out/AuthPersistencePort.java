package utp.siga.identity.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import utp.siga.identity.domain.model.Account;

public interface AuthPersistencePort {
  Optional<Account> byUsernameForUpdate(String username);

  Account accountForUpdate(UUID userId);

  Set<String> permissions(UUID userId);

  void recordFailedLogin(UUID userId, int attempts, Instant lockedUntil);

  void clearFailedLogin(UUID userId);

  void enableMfa(UUID userId, String encryptedSecret);

  void confirmMfa(UUID userId, long step);

  void createRefreshSession(
      UUID userId,
      String tokenHash,
      UUID jti,
      UUID familyId,
      Instant expiresAt,
      Instant mfaAuthenticatedAt);

  Optional<UUID> refreshUserId(String tokenHash);

  RefreshSession refreshForUpdate(String tokenHash);

  void revokeFamily(UUID familyId);

  void revokeUser(UUID userId);

  void rotateRefresh(String currentHash, String replacementHash);

  void logout(UUID familyId, UUID userId);
}
