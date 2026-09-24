package utp.siga.identity.infrastructure;

import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import utp.siga.identity.domain.IdentityException;

@Component
public class ChallengeStore {
  private final StringRedisTemplate redis;
  private final long ttl;
  private final int max, rate;

  public ChallengeStore(
      StringRedisTemplate redis,
      @Value("${iam.challenge-seconds}") long ttl,
      @Value("${iam.max-attempts}") int max,
      @Value("${iam.login-rate}") int rate) {
    this.redis = redis;
    this.ttl = ttl;
    this.max = max;
    this.rate = rate;
  }

  public UUID create(UUID user) {
    UUID id = UUID.randomUUID();
    redis.opsForValue().set("iam:challenge:" + id, user.toString(), Duration.ofSeconds(ttl));
    return id;
  }

  public UUID user(UUID challenge) {
    String value = redis.opsForValue().get("iam:challenge:" + challenge);
    if (value == null) throw IdentityException.unauthorized();
    return UUID.fromString(value);
  }

  public void consume(UUID challenge) {
    redis.delete("iam:challenge:" + challenge);
  }

  private long increment(String key, long seconds) {
    var script =
        new DefaultRedisScript<Long>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1])"
                + " end; return n",
            Long.class);
    return Objects.requireNonNull(redis.execute(script, List.of(key), Long.toString(seconds)));
  }

  public void limit(String ip) {
    if (increment("iam:rate:" + Crypto.hash(ip), 60) > rate)
      throw new IdentityException(
          429, "AUTH_RATE_LIMIT", "Demasiados intentos; reintenta más tarde");
  }

  public void attempt(UUID challenge) {
    if (increment("iam:attempt:" + challenge, ttl) > max) {
      consume(challenge);
      throw new IdentityException(429, "AUTH_MFA_LIMIT", "Demasiados intentos MFA");
    }
  }
}
