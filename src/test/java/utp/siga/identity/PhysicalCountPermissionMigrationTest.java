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
class PhysicalCountPermissionMigrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("siga_identity_count_test")
          .withUsername("siga_identity_count_test")
          .withPassword("siga_identity_count_test");

  @BeforeEach
  void resetIsolatedSchema() throws SQLException {
    execute("DROP SCHEMA IF EXISTS iam CASCADE");
  }

  @Test
  void freshInstallThroughV9ProvisionsCanonicalPermissionAndExactRoles() throws SQLException {
    assertThat(flyway(null).migrate().migrationsExecuted).isEqualTo(9);
    assertProvisioning();
  }

  @Test
  void upgradeFromV8PreservesExistingGrantsAndAddsOnlyThreeAssignments() throws SQLException {
    assertThat(flyway(MigrationVersion.fromVersion("8")).migrate().migrationsExecuted).isEqualTo(8);
    long permissions = count("SELECT count(*) FROM iam.permission");
    long grants = count("SELECT count(*) FROM iam.role_permission");
    long users = count("SELECT count(*) FROM iam.user_account");

    assertThat(flyway(null).migrate().migrationsExecuted).isOne();

    assertProvisioning();
    assertThat(count("SELECT count(*) FROM iam.permission")).isEqualTo(permissions + 1);
    assertThat(count("SELECT count(*) FROM iam.role_permission")).isEqualTo(grants + 3);
    assertThat(count("SELECT count(*) FROM iam.user_account")).isEqualTo(users);
  }

  @Test
  void uuidOrCodeCollisionFailsWithoutChangingUnrelatedPermissions() throws SQLException {
    flyway(MigrationVersion.fromVersion("8")).migrate();
    execute("INSERT INTO iam.permission(id, code, description) VALUES "
        + "('10000000-0000-0000-0000-000000000015', 'UNRELATED_PERMISSION', 'Unrelated')");

    assertThatThrownBy(() -> flyway(null).migrate())
        .hasMessageContaining("V9__physical_count_permission.sql failed");
    assertThat(count("SELECT count(*) FROM iam.permission WHERE code='UNRELATED_PERMISSION'"))
        .isOne();
    assertThat(count("SELECT count(*) FROM iam.permission WHERE code='INVENTORY_COUNT'"))
        .isZero();
  }

  @Test
  void codeCollisionFailsWithoutReassigningTheUnrelatedPermission() throws SQLException {
    flyway(MigrationVersion.fromVersion("8")).migrate();
    execute("INSERT INTO iam.permission(id, code, description) VALUES "
        + "('20000000-0000-0000-0000-000000000015', 'INVENTORY_COUNT', 'Unrelated')");

    assertThatThrownBy(() -> flyway(null).migrate())
        .hasMessageContaining("V9__physical_count_permission.sql failed");
    assertThat(count("SELECT count(*) FROM iam.permission WHERE "
        + "id='20000000-0000-0000-0000-000000000015' AND code='INVENTORY_COUNT'"))
        .isOne();
    assertThat(count("SELECT count(*) FROM iam.permission WHERE "
        + "id='10000000-0000-0000-0000-000000000015'"))
        .isZero();
  }

  private void assertProvisioning() throws SQLException {
    assertThat(count("SELECT count(*) FROM iam.permission WHERE "
        + "id='10000000-0000-0000-0000-000000000015' AND code='INVENTORY_COUNT'"))
        .isOne();
    for (String role : new String[] {"ADMIN", "ENCARGADO", "SUPERVISOR"}) {
      assertThat(permissionCount(role)).isOne();
    }
    assertThat(count("SELECT count(*) FROM iam.role_permission rp "
        + "JOIN iam.permission p ON p.id=rp.permission_id "
        + "WHERE p.code='INVENTORY_COUNT'"))
        .isEqualTo(3);
  }

  private long permissionCount(String role) throws SQLException {
    return count("SELECT count(*) FROM iam.role_permission rp "
        + "JOIN iam.role r ON r.id=rp.role_id "
        + "JOIN iam.permission p ON p.id=rp.permission_id "
        + "WHERE r.code='" + role + "' AND p.code='INVENTORY_COUNT'");
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
