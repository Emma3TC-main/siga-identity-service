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
class EvidencePermissionsMigrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("siga_identity_evidence_test")
          .withUsername("siga_identity_evidence_test")
          .withPassword("siga_identity_evidence_test");

  @BeforeEach
  void resetIsolatedSchema() throws SQLException {
    try (Connection connection = connection(); Statement statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA IF EXISTS iam CASCADE");
    }
  }

  @Test
  void freshInstallThroughV7ProvisionsEvidencePermissions() throws SQLException {
    assertThat(flyway(MigrationVersion.fromVersion("7")).migrate().migrationsExecuted).isEqualTo(7);
    assertV7Provisioning();
  }

  @Test
  void upgradeFromV6ToV7PreservesExistingPermissionsAndAddsExactAssignments()
      throws SQLException {
    assertThat(flyway(MigrationVersion.fromVersion("6")).migrate().migrationsExecuted).isEqualTo(6);
    assertThat(count("SELECT count(*) FROM iam.permission")).isEqualTo(11);

    assertThat(flyway(MigrationVersion.fromVersion("7")).migrate().migrationsExecuted).isOne();

    assertV7Provisioning();
  }

  @Test
  void uuidCollisionFailsInsteadOfChangingAnUnrelatedPermission() throws SQLException {
    flyway(MigrationVersion.fromVersion("6")).migrate();
    execute("INSERT INTO iam.permission(id, code, description) VALUES "
        + "('10000000-0000-0000-0000-000000000012', 'UNRELATED_PERMISSION', 'Unrelated')");

    assertThatThrownBy(() -> flyway(MigrationVersion.fromVersion("7")).migrate())
        .hasMessageContaining("V7__evidence_permissions.sql failed");
    assertThat(count("SELECT count(*) FROM iam.permission WHERE code='UNRELATED_PERMISSION'"))
        .isOne();
    assertThat(count("SELECT count(*) FROM iam.permission WHERE code='EVIDENCE_READ'"))
        .isZero();
  }

  @Test
  void codeCollisionFailsInsteadOfChangingAnUnrelatedPermission() throws SQLException {
    flyway(MigrationVersion.fromVersion("6")).migrate();
    execute("INSERT INTO iam.permission(id, code, description) VALUES "
        + "('20000000-0000-0000-0000-000000000012', 'EVIDENCE_READ', 'Unrelated')");

    assertThatThrownBy(() -> flyway(MigrationVersion.fromVersion("7")).migrate())
        .hasMessageContaining("V7__evidence_permissions.sql failed");
    assertThat(count("SELECT count(*) FROM iam.permission"
        + " WHERE id='20000000-0000-0000-0000-000000000012' AND code='EVIDENCE_READ'"))
        .isOne();
    assertThat(count("SELECT count(*) FROM iam.permission"
        + " WHERE id='10000000-0000-0000-0000-000000000012'"))
        .isZero();
  }

  private void assertV7Provisioning() throws SQLException {
    assertThat(count("SELECT count(*) FROM iam.permission")).isEqualTo(13);
    assertThat(count("SELECT count(*) FROM iam.permission WHERE "
        + "(id='10000000-0000-0000-0000-000000000012' AND code='EVIDENCE_READ') OR "
        + "(id='10000000-0000-0000-0000-000000000013' AND code='EVIDENCE_WRITE')"))
        .isEqualTo(2);
    for (String role : new String[] {"ADMIN", "ENCARGADO", "SUPERVISOR"}) {
      assertThat(permissionCount(role, "EVIDENCE_READ")).isOne();
      assertThat(permissionCount(role, "EVIDENCE_WRITE")).isOne();
    }
    assertThat(count("SELECT count(*) FROM iam.role_permission rp"
        + " JOIN iam.permission p ON p.id=rp.permission_id"
        + " WHERE p.code IN ('EVIDENCE_READ','EVIDENCE_WRITE')"))
        .isEqualTo(6);
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
