package utp.siga.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class TraceabilityManageMigrationTest {
  private static final String PERMISSION_ID = "10000000-0000-0000-0000-000000000017";

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("siga_identity_traceability_test")
          .withUsername("siga_identity_traceability_test")
          .withPassword("siga_identity_traceability_test");

  @BeforeEach
  void resetIsolatedSchema() throws SQLException {
    execute("DROP SCHEMA IF EXISTS iam CASCADE");
  }

  @Test
  void freshInstallThroughV11ProvisionsCanonicalPermissionForAllOperationalRoles()
      throws SQLException {
    assertThat(flyway(null).migrate().migrationsExecuted).isEqualTo(11);
    assertProvisioning();
  }

  @Test
  void upgradeFromV10PreservesIamStateAndAddsOnlyTheCanonicalDelta() throws SQLException {
    assertThat(flyway(MigrationVersion.fromVersion("10")).migrate().migrationsExecuted).isEqualTo(10);
    execute("UPDATE iam.user_account SET mfa_enabled=true, mfa_secret_encrypted='test-only', "
        + "mfa_enrolled=true, last_totp_step=12345, failed_login_attempts=2 "
        + "WHERE username='admin.demo'");
    execute("INSERT INTO iam.refresh_token(id,user_id,token_hash,jti,family_id,expires_at,"
        + "mfa_authenticated_at) SELECT '13000000-0000-0000-0000-000000000011',id,'test-hash',"
        + "'14000000-0000-0000-0000-000000000011','15000000-0000-0000-0000-000000000011',"
        + "now()+interval '1 day',now() FROM iam.user_account WHERE username='admin.demo'");

    List<String> usersBefore = userRows();
    List<String> refreshTokensBefore = refreshTokenRows();
    List<String> rolesBefore = rows("SELECT id||'|'||code||'|'||name FROM iam.role ORDER BY id");
    List<String> permissionsBefore = rows(
        "SELECT id||'|'||code||'|'||description FROM iam.permission ORDER BY id");
    List<String> assignmentsBefore = rows(
        "SELECT role_id||'|'||permission_id FROM iam.role_permission ORDER BY role_id,permission_id");

    assertThat(flyway(null).migrate().migrationsExecuted).isOne();

    assertThat(userRows()).containsExactlyElementsOf(usersBefore);
    assertThat(refreshTokenRows()).containsExactlyElementsOf(refreshTokensBefore);
    assertThat(rows("SELECT id||'|'||code||'|'||name FROM iam.role ORDER BY id"))
        .containsExactlyElementsOf(rolesBefore);
    assertThat(rows("SELECT id||'|'||code||'|'||description FROM iam.permission "
        + "WHERE code <> 'TRACEABILITY_MANAGE' ORDER BY id"))
        .containsExactlyElementsOf(permissionsBefore);
    assertThat(rows("SELECT role_id||'|'||permission_id FROM iam.role_permission "
        + "WHERE permission_id <> '" + PERMISSION_ID + "' ORDER BY role_id,permission_id"))
        .containsExactlyElementsOf(assignmentsBefore);
    assertThat(count("SELECT count(*) FROM iam.permission")).isEqualTo(permissionsBefore.size() + 1L);
    assertThat(count("SELECT count(*) FROM iam.role_permission"))
        .isEqualTo(assignmentsBefore.size() + 3L);
    assertProvisioning();
  }

  @Test
  void uuidCollisionFailsWithoutChangingTheUnrelatedPermission() throws SQLException {
    flyway(MigrationVersion.fromVersion("10")).migrate();
    execute("INSERT INTO iam.permission(id, code, description) VALUES "
        + "('" + PERMISSION_ID + "', 'UNRELATED_PERMISSION', 'Unrelated')");

    assertThatThrownBy(() -> flyway(null).migrate())
        .hasMessageContaining("V11__traceability_manage_permission.sql failed");
    assertThat(count("SELECT count(*) FROM iam.permission WHERE code='UNRELATED_PERMISSION'"))
        .isOne();
    assertThat(count("SELECT count(*) FROM iam.permission WHERE code='TRACEABILITY_MANAGE'"))
        .isZero();
  }

  @Test
  void codeCollisionFailsWithoutReassigningTheUnrelatedPermission() throws SQLException {
    flyway(MigrationVersion.fromVersion("10")).migrate();
    execute("INSERT INTO iam.permission(id, code, description) VALUES "
        + "('20000000-0000-0000-0000-000000000017', 'TRACEABILITY_MANAGE', 'Unrelated')");

    assertThatThrownBy(() -> flyway(null).migrate())
        .hasMessageContaining("V11__traceability_manage_permission.sql failed");
    assertThat(count("SELECT count(*) FROM iam.permission WHERE "
        + "id='20000000-0000-0000-0000-000000000017' AND code='TRACEABILITY_MANAGE'"))
        .isOne();
    assertThat(count("SELECT count(*) FROM iam.permission WHERE id='" + PERMISSION_ID + "'"))
        .isZero();
  }

  private void assertProvisioning() throws SQLException {
    assertThat(count("SELECT count(*) FROM iam.permission WHERE id='" + PERMISSION_ID
        + "' AND code='TRACEABILITY_MANAGE' AND description='Gestionar lotes y activos'"))
        .isOne();
    assertThat(permissionCount("ADMIN")).isOne();
    assertThat(permissionCount("SUPERVISOR")).isOne();
    assertThat(permissionCount("ENCARGADO")).isOne();
    assertThat(count("SELECT count(*) FROM iam.role_permission rp JOIN iam.permission p "
        + "ON p.id=rp.permission_id WHERE p.code='TRACEABILITY_MANAGE'"))
        .isEqualTo(3);
  }

  private long permissionCount(String role) throws SQLException {
    return count("SELECT count(*) FROM iam.role_permission rp JOIN iam.role r ON r.id=rp.role_id "
        + "JOIN iam.permission p ON p.id=rp.permission_id "
        + "WHERE r.code='" + role + "' AND p.code='TRACEABILITY_MANAGE'");
  }

  private List<String> rows(String sql) throws SQLException {
    var result = new ArrayList<String>();
    try (Connection connection = connection(); Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      while (rows.next()) result.add(rows.getString(1));
    }
    return result;
  }

  private List<String> userRows() throws SQLException {
    return rows("SELECT row_to_json(u)::text FROM (SELECT id,username,email,password_hash,active,"
        + "mfa_enabled,mfa_secret_encrypted,failed_login_attempts,locked_until,version,created_at,"
        + "updated_at,mfa_enrolled,last_totp_step FROM iam.user_account ORDER BY id) u");
  }

  private List<String> refreshTokenRows() throws SQLException {
    return rows("SELECT row_to_json(t)::text FROM (SELECT id,user_id,token_hash,jti,family_id,"
        + "expires_at,revoked_at,replaced_by_jti,created_at,mfa_authenticated_at "
        + "FROM iam.refresh_token ORDER BY id) t");
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
