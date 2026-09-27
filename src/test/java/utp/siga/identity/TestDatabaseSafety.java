package utp.siga.identity;

import java.net.URI;
import java.util.Set;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/** Test-classpath-only guard. Runs before singleton creation, including DataSource and Flyway. */
public final class TestDatabaseSafety
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private static final Set<String> DATABASES =
      Set.of("/siga_identity_local_test", "/siga_identity_test");

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    context.addBeanFactoryPostProcessor(factory -> validate(context.getEnvironment()));
  }

  static void validate(Environment env) {
    String url = env.getProperty("spring.datasource.url", "");
    try {
      if (!url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
      URI uri = URI.create(url.substring(5));
      if (uri.getHost() == null || !DATABASES.contains(uri.getRawPath())
          || uri.getRawQuery() != null || uri.getRawFragment() != null
          || uri.getRawUserInfo() != null) throw new IllegalArgumentException();
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException("TEST_DATABASE_GUARD: only dedicated IAM test database URLs without parameters are allowed");
    }
    if (!"siga_iam_test".equals(env.getProperty("spring.datasource.username"))) {
      throw new IllegalStateException("TEST_DATABASE_GUARD: dedicated siga_iam_test login required");
    }
    // Prevent independent Flyway/pool/JNDI connections from bypassing the checked URL.
    for (String key : Set.of("spring.flyway.url", "spring.flyway.user", "spring.flyway.password",
        "spring.datasource.jndi-name", "spring.datasource.hikari.jdbc-url",
        "spring.datasource.type", "spring.datasource.hikari.username", "spring.datasource.hikari.password",
        "spring.datasource.hikari.data-source-class-name", "spring.datasource.hikari.data-source-j-n-d-i")) {
      if (env.containsProperty(key)) {
        throw new IllegalStateException("TEST_DATABASE_GUARD: connection override forbidden: " + key);
      }
    }
    if (org.springframework.boot.context.properties.bind.Binder.get(env)
        .bind("spring.datasource.hikari.data-source-properties",
            org.springframework.boot.context.properties.bind.Bindable.mapOf(String.class, String.class))
        .isBound()) {
      throw new IllegalStateException("TEST_DATABASE_GUARD: pool connection properties forbidden");
    }
    if (!"1".equals(env.getProperty("spring.data.redis.database"))
        || env.containsProperty("spring.data.redis.url")) {
      throw new IllegalStateException("TEST_DATABASE_GUARD: dedicated Redis database 1 required; URL overrides forbidden");
    }
  }
}
