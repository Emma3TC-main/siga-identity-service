package utp.siga.identity.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import utp.siga.identity.infrastructure.persistence.IdentityRepository;

@Component
public class Bootstrap implements ApplicationRunner {
  private final IdentityRepository repo;
  private final PasswordEncoder encoder;
  private final String password;

  public Bootstrap(
      IdentityRepository repo,
      PasswordEncoder encoder,
      @Value("${iam.bootstrap-password}") String password) {
    this.repo = repo;
    this.encoder = encoder;
    this.password = password;
  }

  @Transactional
  public void run(ApplicationArguments args) {
    if (password.isEmpty()) return;
    if (password.length() < 12)
      throw new IllegalArgumentException("Bootstrap password requires at least 12 characters");
    repo.jdbc()
        .update(
            "UPDATE iam.user_account SET password_hash=? WHERE username='admin.demo' AND"
                + " password_hash='$argon2id$DEMO_REPLACE_WITH_REAL_HASH'",
            encoder.encode(password));
  }
}
