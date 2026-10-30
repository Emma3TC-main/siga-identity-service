package utp.siga.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import utp.siga.identity.infrastructure.config.Bootstrap;
import utp.siga.identity.infrastructure.persistence.IdentityRepository;

class BootstrapTest {
  @Test
  void replacesEachPlaceholderOnlyFromItsOwnConfiguredSecret() throws Exception {
    IdentityRepository repository = mock(IdentityRepository.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(repository.jdbc()).thenReturn(jdbc);
    when(encoder.encode("admin-password-123")).thenReturn("admin-hash");
    when(encoder.encode("operator-password-123")).thenReturn("operator-hash");

    new Bootstrap(repository, encoder, "admin-password-123", "operator-password-123")
        .run(new DefaultApplicationArguments());

    verify(jdbc).update(
        "UPDATE iam.user_account SET password_hash=? WHERE username=? AND password_hash=?",
        "admin-hash", "admin.demo", "$argon2id$DEMO_REPLACE_WITH_REAL_HASH");
    verify(jdbc).update(
        "UPDATE iam.user_account SET password_hash=? WHERE username=? AND password_hash=?",
        "operator-hash", "encargado.demo", "$argon2id$ENCARGADO_DEMO_REPLACE_WITH_REAL_HASH");
  }

  @Test
  void absentOperatorSecretNeverChangesOperatorCredential() throws Exception {
    IdentityRepository repository = mock(IdentityRepository.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(repository.jdbc()).thenReturn(jdbc);

    new Bootstrap(repository, encoder, "", "").run(new DefaultApplicationArguments());

    verify(jdbc, never()).update(any(String.class), any(), any(), any());
    verify(encoder, never()).encode(any());
  }
}
