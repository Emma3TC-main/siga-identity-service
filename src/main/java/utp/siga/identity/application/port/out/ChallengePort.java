package utp.siga.identity.application.port.out;

import java.util.UUID;

public interface ChallengePort {
  void limit(String ip);

  UUID create(UUID userId);

  UUID user(UUID challengeId);

  void attempt(UUID challengeId);

  void consume(UUID challengeId);
}
