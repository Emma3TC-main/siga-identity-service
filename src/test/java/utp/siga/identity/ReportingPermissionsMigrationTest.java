package utp.siga.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class ReportingPermissionsMigrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("siga_identity_reporting_test")
          .withUsername("siga_identity_reporting_test")
          .withPassword("siga_identity_reporting_test");

  @BeforeEach
  void resetIsolatedSchema() throws SQLException {
    try (Connection connection = connection(); Statement statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA IF EXISTS iam CASCADE");
    }
  }

  @Test
  void freshInstallThroughV8ProvisionsCanonicalReportingPermissions() throws SQLException {
    assertThat(flyway(MigrationVersion.fromVersion("8")).migrate().migrationsExecuted).isEqualTo(8);
    assertV8Provisioning();
  }

  @Test
  void upgradeFromV7ToV8PreservesExistingGrantsAndAddsOnlyReportingDelta()
      throws SQLException {
    assertThat(flyway(MigrationVersion.fromVersion("7")).migrate().migrationsExecuted).isEqualTo(7);
    long existingGrants = count("SELECT count(*) FROM iam.role_permission");

    assertThat(flyway(MigrationVersion.fromVersion("8")).migrate().migrationsExecuted).isOne();

    assertV8Provisioning();
    assertThat(count("SELECT count(*) FROM iam.role_permission")).isEqualTo(existingGrants + 2);
  }

  @Test
  void uuidCollisionFailsWithoutChangingTheUnrelatedPermission() throws SQLException {
    flyway(MigrationVersion.fromVersion("7")).migrate();
    execute("INSERT INTO iam.permission(id, code, description) VALUES "
        + "('10000000-0000-0000-0000-000000000014', 'UNRELATED_PERMISSION', 'Unrelated')");

    assertThatThrownBy(() -> flyway(MigrationVersion.fromVersion("8")).migrate())
        .hasMessageContaining("V8__reporting_permissions.sql failed");
    assertThat(count("SELECT count(*) FROM iam.permission WHERE code='UNRELATED_PERMISSION'"))
        .isOne();
    assertThat(count("SELECT count(*) FROM iam.permission WHERE code='ANALYTICS_READ'"))
        .isZero();
  }

  private void assertV8Provisioning() throws SQLException {
    assertThat(count("SELECT count(*) FROM iam.permission WHERE "
        + "id='10000000-0000-0000-0000-000000000014' AND code='ANALYTICS_READ'"))
        .isOne();
    assertThat(permissionCount("ADMIN", "REPORT_READ")).isOne();
    assertThat(permissionCount("ADMIN", "ANALYTICS_READ")).isOne();
    assertThat(permissionCount("SUPERVISOR", "REPORT_READ")).isOne();
    assertThat(permissionCount("SUPERVISOR", "ANALYTICS_READ")).isZero();
    assertThat(permissionCount("ENCARGADO", "REPORT_READ")).isZero();
    assertThat(permissionCount("ENCARGADO", "ANALYTICS_READ")).isZero();
  }

  private long permissionCount(String role, String permission) throws SQLException {
    return count("SELECT count(*) FROM iam.role_permission rp"
        + " JOIN iam.role r ON r.id=rp.role_id"
        + " JOIN iam.permission p ON p.id=rp.permission_id"
        + " WHERE r.code='" + role + "' AND p.code='" + permission + "'");
  }

  private void execute(String sql) throws SQLException {
    try (Connection connection = connection(); Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
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
