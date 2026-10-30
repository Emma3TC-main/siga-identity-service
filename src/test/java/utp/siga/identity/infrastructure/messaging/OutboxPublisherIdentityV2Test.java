package utp.siga.identity.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import utp.siga.identity.application.dto.Api;
import utp.siga.identity.application.event.IdentityAuditEvent;

class OutboxPublisherIdentityV2Test {
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  private static final UUID ACTOR_ID = UUID.fromString("12000000-0000-0000-0000-000000000001");

  @ParameterizedTest(name = "{0}")
  @MethodSource("events")
  void routesAndSerializesActualOutboxEnvelopeAgainstCanonicalSchema(
      String eventType,
      String aggregateType,
      String routingKey,
      String schemaFile,
      Object payload) throws Exception {
    UUID aggregateId = aggregateType.equals("User")
        ? ((IdentityAuditEvent.UserPayload) payload).userId()
        : ((IdentityAuditEvent.RolePayload) payload).roleId();
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", UUID.randomUUID());
    row.put("event_type", eventType);
    row.put("schema_version", 2);
    row.put("aggregate_type", aggregateType);
    row.put("aggregate_id", aggregateId);
    row.put("correlation_id", UUID.randomUUID());
    row.put("causation_id", null);
    row.put("occurred_at", Timestamp.from(Instant.parse("2026-10-29T20:00:00Z")));
    row.put("payload", JSON.writeValueAsString(payload));

    assertThat(OutboxPublisher.routingKey(eventType, 2)).isEqualTo(routingKey);
    JsonNode envelope = JSON.valueToTree(OutboxPublisher.envelope(row, JSON));
    String serialized = JSON.writeValueAsString(envelope).toLowerCase();
    assertThat(serialized)
        .doesNotContain("password")
        .doesNotContain("hash")
        .doesNotContain("token")
        .doesNotContain("secret")
        .doesNotContain("otp")
        .doesNotContain("authorization");
    Path schemaPath = Path.of("..", "siga-documentation", "SIGA_Documentacion_Tecnica_Final_v1.2", "api", schemaFile);
    try (var schemaStream = Files.newInputStream(schemaPath)) {
      var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaStream);
      assertThat(schema.validate(envelope)).isEmpty();
    }
  }

  private static Stream<Arguments> events() {
    UUID userId = UUID.randomUUID();
    UUID roleId = UUID.randomUUID();
    UUID permissionId = UUID.randomUUID();
    Api.User enabled = new Api.User(userId, "user", "user@siga.test", true, false, Set.of(roleId));
    Api.User disabled = new Api.User(userId, "user", "user@siga.test", false, false, Set.of(roleId));
    Api.Role role = new Api.Role(roleId, "WAREHOUSE", "Warehouse", true, Set.of(permissionId));
    Api.Role changedRole = new Api.Role(roleId, "WAREHOUSE", "Warehouse", true, Set.of());
    return Stream.of(
        Arguments.of("UserCreated", "User", "iam.user.created.v2", "iam-user-created-v2.schema.json",
            new IdentityAuditEvent.UserPayload(userId, ACTOR_ID, null, enabled)),
        Arguments.of("UserUpdated", "User", "iam.user.updated.v2", "iam-user-updated-v2.schema.json",
            new IdentityAuditEvent.UserPayload(userId, ACTOR_ID, enabled, enabled)),
        Arguments.of("UserDisabled", "User", "iam.user.disabled.v2", "iam-user-disabled-v2.schema.json",
            new IdentityAuditEvent.UserPayload(userId, ACTOR_ID, enabled, disabled)),
        Arguments.of("UserRolesChanged", "User", "iam.user.roles.changed.v2", "iam-user-roles-changed-v2.schema.json",
            new IdentityAuditEvent.UserPayload(userId, ACTOR_ID, enabled, enabled)),
        Arguments.of("RoleCreated", "Role", "iam.role.created.v2", "iam-role-created-v2.schema.json",
            new IdentityAuditEvent.RolePayload(roleId, ACTOR_ID, null, role)),
        Arguments.of("RolePermissionsChanged", "Role", "iam.role.permissions.changed.v2", "iam-role-permissions-changed-v2.schema.json",
            new IdentityAuditEvent.RolePayload(roleId, ACTOR_ID, role, changedRole)));
  }
}
