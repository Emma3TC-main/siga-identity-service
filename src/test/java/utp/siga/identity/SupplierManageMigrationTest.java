package utp.siga.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class SupplierManageMigrationTest {
  private static final UUID SUPPLIER_MANAGE_ID =
      UUID.fromString("10000000-0000-0000-0000-000000000010");
  private static final UUID ADMIN_ID =
      UUID.fromString("11000000-0000-0000-0000-000000000001");

  @Test
  @EnabledIfEnvironmentVariable(named = "SIGA_MIGRATION_FRESH_DB_URL", matches = ".+_test$")
  void freshInstallThroughV5ProvisionsSupplierManageOnlyForAdmin() throws SQLException {
    String url = System.getenv("SIGA_MIGRATION_FRESH_DB_URL");

    var result = flyway(url, null).migrate();

    assertThat(result.migrationsExecuted).isEqualTo(5);
    assertProvisioning(url);
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "SIGA_MIGRATION_UPGRADE_DB_URL", matches = ".+_test$")
  void upgradeFromV4ToV5PreservesExistingRbacAndAddsOnlySupplierManage() throws SQLException {
    String url = System.getenv("SIGA_MIGRATION_UPGRADE_DB_URL");

    var baseline = flyway(url, MigrationVersion.fromVersion("4")).migrate();
    assertThat(baseline.migrationsExecuted).isEqualTo(4);

    Map<UUID, Permission> permissionsBefore = permissions(url);
    Set<RolePermission> assignmentsBefore = rolePermissions(url);
    assertThat(permissionsBefore).hasSize(9).doesNotContainKey(SUPPLIER_MANAGE_ID);
    assertThat(assignmentsBefore).hasSize(9);

    var upgrade = flyway(url, null).migrate();

    assertThat(upgrade.migrationsExecuted).isEqualTo(1);
    assertThat(permissions(url)).containsAllEntriesOf(permissionsBefore);
    assertThat(rolePermissions(url))
        .containsAll(assignmentsBefore)
        .containsOnlyOnce(new RolePermission(ADMIN_ID, SUPPLIER_MANAGE_ID));
    assertProvisioning(url);
  }

  private static Flyway flyway(String url, MigrationVersion target) {
    var configuration =
        Flyway.configure()
            .dataSource(url, requiredEnvironment("SIGA_MIGRATION_DB_USER"),
                requiredEnvironment("SIGA_MIGRATION_DB_PASSWORD"))
            .defaultSchema("iam")
            .schemas("iam");
    if (target != null) configuration.target(target);
    return configuration.load();
  }

  private static void assertProvisioning(String url) throws SQLException {
    assertThat(permissions(url))
        .hasSize(10)
        .containsEntry(SUPPLIER_MANAGE_ID,
            new Permission("SUPPLIER_MANAGE", "Gestionar proveedores"));
    assertThat(permissionCountForRole(url, "ADMIN", "SUPPLIER_MANAGE")).isOne();
    assertThat(permissionCountForRole(url, "ENCARGADO", "SUPPLIER_MANAGE")).isZero();
    assertThat(permissionCountForRole(url, "SUPERVISOR", "SUPPLIER_MANAGE")).isZero();
    assertThat(totalPermissionsForRole(url, "ADMIN")).isEqualTo(10);
    assertThat(totalPermissionsForRole(url, "ENCARGADO")).isZero();
    assertThat(totalPermissionsForRole(url, "SUPERVISOR")).isZero();
  }

  private static Map<UUID, Permission> permissions(String url) throws SQLException {
    var result = new LinkedHashMap<UUID, Permission>();
    try (Connection connection = connection(url);
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(
            "SELECT id,code,description FROM iam.permission ORDER BY id")) {
      while (rows.next()) {
        result.put(
            rows.getObject("id", UUID.class),
            new Permission(rows.getString("code"), rows.getString("description")));
      }
    }
    return result;
  }

  private static Set<RolePermission> rolePermissions(String url) throws SQLException {
    var result = new LinkedHashSet<RolePermission>();
    try (Connection connection = connection(url);
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(
            "SELECT role_id,permission_id FROM iam.role_permission"
                + " ORDER BY role_id,permission_id")) {
      while (rows.next()) {
        result.add(new RolePermission(
            rows.getObject("role_id", UUID.class), rows.getObject("permission_id", UUID.class)));
      }
    }
    return result;
  }

  private static long permissionCountForRole(String url, String roleCode, String permissionCode)
      throws SQLException {
    return count(url,
        "SELECT count(*) FROM iam.role_permission rp"
            + " JOIN iam.role r ON r.id=rp.role_id"
            + " JOIN iam.permission p ON p.id=rp.permission_id"
            + " WHERE r.code='" + roleCode + "' AND p.code='" + permissionCode + "'");
  }

  private static long totalPermissionsForRole(String url, String roleCode) throws SQLException {
    return count(url,
        "SELECT count(*) FROM iam.role_permission rp"
            + " JOIN iam.role r ON r.id=rp.role_id WHERE r.code='" + roleCode + "'");
  }

  private static long count(String url, String sql) throws SQLException {
    try (Connection connection = connection(url);
        Statement statement = connection.createStatement();
        ResultSet row = statement.executeQuery(sql)) {
      row.next();
      return row.getLong(1);
    }
  }

  private static Connection connection(String url) throws SQLException {
    return DriverManager.getConnection(
        url,
        requiredEnvironment("SIGA_MIGRATION_DB_USER"),
        requiredEnvironment("SIGA_MIGRATION_DB_PASSWORD"));
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
    return value;
  }

  private record Permission(String code, String description) {}

  private record RolePermission(UUID roleId, UUID permissionId) {}
}
