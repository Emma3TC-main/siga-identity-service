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
  private final String operatorPassword;

  public Bootstrap(
      IdentityRepository repo,
      PasswordEncoder encoder,
      @Value("${iam.bootstrap-password}") String password,
      @Value("${iam.operator-bootstrap-password:}") String operatorPassword) {
    this.repo = repo;
    this.encoder = encoder;
    this.password = password;
    this.operatorPassword = operatorPassword;
  }

  @Transactional
  public void run(ApplicationArguments args) {
    replacePlaceholder("admin.demo", "$argon2id$DEMO_REPLACE_WITH_REAL_HASH", password);
    replacePlaceholder("encargado.demo", "$argon2id$ENCARGADO_DEMO_REPLACE_WITH_REAL_HASH",
        operatorPassword);
  }

  private void replacePlaceholder(String username, String placeholder, String value) {
    if (value.isEmpty()) return;
    if (value.length() < 12)
      throw new IllegalArgumentException("Bootstrap password requires at least 12 characters");
    repo.jdbc().update(
        "UPDATE iam.user_account SET password_hash=? WHERE username=? AND password_hash=?",
        encoder.encode(value), username, placeholder);
  }
}
