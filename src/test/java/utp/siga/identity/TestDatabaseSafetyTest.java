package utp.siga.identity;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class TestDatabaseSafetyTest {
  private MockEnvironment safe() {
    return new MockEnvironment()
        .withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:35432/siga_identity_local_test")
        .withProperty("spring.datasource.username", "siga_iam_test")
        .withProperty("spring.data.redis.database", "1");
  }

  @Test
  void dedicatedConfigurationIsAccepted() {
    assertThatCode(() -> TestDatabaseSafety.validate(safe())).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"jdbc:postgresql://localhost/siga", "jdbc:postgresql://localhost/other_test",
      "jdbc:postgresql://localhost/siga_identity_local_test?currentSchema=public",
      "jdbc:postgresql://localhost/siga_identity_local_test?databaseName=siga",
      "jdbc:postgresql://localhost/siga_identity_local_%74est", "jdbc:postgresql:siga",
      "jdbc:postgresql://localhost/siga_identity_local_test#siga", ""})
  void rejectsUnsafeUrlsBeforeAnySingletonCanWrite(String url) {
    AtomicBoolean initialized = new AtomicBoolean();
    try (var context = new GenericApplicationContext()) {
      context.setEnvironment(safe().withProperty("spring.datasource.url", url));
      context.registerBean("migrationOrWriter", Object.class, () -> {
        initialized.set(true);
        return new Object();
      });
      new TestDatabaseSafety().initialize(context);
      assertThatThrownBy(context::refresh).hasMessageContaining("TEST_DATABASE_GUARD");
      assertThat(initialized).isFalse();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"spring.flyway.url", "spring.flyway.user", "spring.datasource.hikari.jdbc-url",
      "spring.datasource.jndi-name", "spring.data.redis.url"})
  void rejectsAlternativeConnections(String key) {
    assertThatThrownBy(() -> TestDatabaseSafety.validate(safe().withProperty(key, "unsafe")))
        .hasMessageContaining("TEST_DATABASE_GUARD");
  }

  @Test
  void rejectsOperationalLoginAndRedisDatabase() {
    assertThatThrownBy(() -> TestDatabaseSafety.validate(safe().withProperty("spring.datasource.username", "siga_iam")))
        .hasMessageContaining("TEST_DATABASE_GUARD");
    assertThatThrownBy(() -> TestDatabaseSafety.validate(safe().withProperty("spring.data.redis.database", "0")))
        .hasMessageContaining("TEST_DATABASE_GUARD");
  }
}
