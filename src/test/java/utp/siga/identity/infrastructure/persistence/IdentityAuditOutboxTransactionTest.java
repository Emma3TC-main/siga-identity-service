package utp.siga.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import utp.siga.identity.application.dto.Api;
import utp.siga.identity.application.port.out.AuthenticatedActorPort;
import utp.siga.identity.application.port.out.CredentialPort;
import utp.siga.identity.application.port.out.CryptoPort;
import utp.siga.identity.application.usecase.ManagementService;

@Testcontainers
class IdentityAuditOutboxTransactionTest {
  private static final UUID ACTOR_ID = UUID.fromString("12000000-0000-0000-0000-000000000001");

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("siga_identity_audit_v2_test")
          .withUsername("siga_identity_audit_v2_test")
          .withPassword("siga_identity_audit_v2_test");

  private JdbcTemplate jdbc;
  private TransactionTemplate transactions;
  private ManagementService service;

  @BeforeEach
  void setUp() {
    var dataSource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DROP SCHEMA IF EXISTS iam CASCADE");
    Flyway.configure().dataSource(dataSource).defaultSchema("iam").schemas("iam").load().migrate();
    var repository = new IdentityRepository(jdbc, new ObjectMapper().findAndRegisterModules());
    var actor = mock(AuthenticatedActorPort.class);
    when(actor.currentUserId()).thenReturn(ACTOR_ID);
    service = new ManagementService(
        repository, repository, actor, mock(CredentialPort.class), mock(CryptoPort.class));
    transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
  }

  @Test
  void effectiveMutationAndPendingV2EventCommitTogether() {
    Api.Role role = transactions.execute(status ->
        service.createRole(new Api.CreateRole("AUDITOR_TEST", "Auditor test", Set.of())));

    assertThat(role).isNotNull();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM iam.role WHERE id=?", Long.class, role.id()))
        .isOne();
    Map<String, Object> event = jdbc.queryForMap(
        "SELECT aggregate_type,aggregate_id,event_type,schema_version,status,attempts,payload "
            + "FROM iam.outbox_event WHERE aggregate_id=?",
        role.id());
    assertThat(event)
        .containsEntry("aggregate_type", "Role")
        .containsEntry("aggregate_id", role.id())
        .containsEntry("event_type", "RoleCreated")
        .containsEntry("schema_version", 2)
        .containsEntry("status", "PENDING")
        .containsEntry("attempts", 0);
  }

  @Test
  void rollbackRemovesBothMutationAndEvent() {
    assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
      service.createRole(new Api.CreateRole("ROLLBACK_TEST", "Rollback test", Set.of()));
      throw new IllegalStateException("force rollback");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM iam.role WHERE code='ROLLBACK_TEST'", Long.class)).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM iam.outbox_event WHERE event_type='RoleCreated'", Long.class)).isZero();
  }
}
