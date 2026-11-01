package utp.siga.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RoleApprovalMigrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("siga_identity_role_test")
          .withUsername("siga_identity_role_test")
          .withPassword("siga_identity_role_test");

  @BeforeEach
  void resetIsolatedSchema() throws SQLException {
    try (Connection connection = connection(); Statement statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA IF EXISTS iam CASCADE");
    }
  }

  @Test
  void freshInstallThroughV6ProvisionsCatalogReadAndOperationalRoles() throws SQLException {
    var result = flyway(MigrationVersion.fromVersion("6")).migrate();

    assertThat(result.migrationsExecuted).isEqualTo(6);
    assertV6Provisioning();
  }

  @Test
  void upgradeFromV5ToV6PreservesExistingPermissionsAndAddsExactAssignments()
      throws SQLException {
    assertThat(flyway(MigrationVersion.fromVersion("5")).migrate().migrationsExecuted).isEqualTo(5);
    assertThat(count("SELECT count(*) FROM iam.permission")).isEqualTo(10);
    assertThat(count("SELECT count(*) FROM iam.user_account WHERE username='encargado.demo'"))
        .isZero();

    assertThat(flyway(MigrationVersion.fromVersion("6")).migrate().migrationsExecuted).isOne();

    assertV6Provisioning();
  }

  private void assertV6Provisioning() throws SQLException {
    assertThat(count("SELECT count(*) FROM iam.permission")).isEqualTo(11);
    assertThat(count("SELECT count(*) FROM iam.permission"
        + " WHERE id='10000000-0000-0000-0000-000000000011' AND code='CATALOG_READ'"))
        .isOne();
    assertThat(permissionCount("ADMIN", "CATALOG_READ")).isOne();
    assertThat(permissionCount("ENCARGADO", "INVENTORY_READ")).isOne();
    assertThat(permissionCount("ENCARGADO", "MOVEMENT_CREATE")).isOne();
    assertThat(permissionCount("ENCARGADO", "CATALOG_READ")).isOne();
    assertThat(permissionCount("ENCARGADO", "MOVEMENT_AUTHORIZE")).isZero();
    assertThat(permissionCount("SUPERVISOR", "INVENTORY_READ")).isOne();
    assertThat(permissionCount("SUPERVISOR", "MOVEMENT_AUTHORIZE")).isOne();
    assertThat(permissionCount("SUPERVISOR", "CATALOG_READ")).isOne();
    assertThat(count("SELECT count(*) FROM iam.user_account u"
        + " JOIN iam.user_role ur ON ur.user_id=u.id"
        + " JOIN iam.role r ON r.id=ur.role_id"
        + " WHERE u.username='encargado.demo' AND u.active AND NOT u.mfa_enabled"
        + " AND r.code='ENCARGADO'"))
        .isOne();
  }

  private long permissionCount(String role, String permission) throws SQLException {
    return count("SELECT count(*) FROM iam.role_permission rp"
        + " JOIN iam.role r ON r.id=rp.role_id"
        + " JOIN iam.permission p ON p.id=rp.permission_id"
        + " WHERE r.code='" + role + "' AND p.code='" + permission + "'");
  }

  private long count(String sql) throws SQLException {
    try (Connection connection = connection(); Statement statement = connection.createStatement();
        ResultSet row = statement.executeQuery(sql)) {
      row.next();
      return row.getLong(1);
    }
  }

  private Flyway flyway(MigrationVersion target) {
    var configuration = Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .defaultSchema("iam")
        .schemas("iam");
    if (target != null) configuration.target(target);
    return configuration.load();
  }

  private Connection connection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
