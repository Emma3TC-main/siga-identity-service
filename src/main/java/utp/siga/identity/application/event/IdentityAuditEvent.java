package utp.siga.identity.application.event;

import java.util.Objects;
import java.util.UUID;
import utp.siga.identity.application.dto.Api;

public record IdentityAuditEvent(
    int schemaVersion,
    String eventType,
    String aggregateType,
    UUID aggregateId,
    Object payload) {

  public IdentityAuditEvent {
    if (schemaVersion != 2) throw new IllegalArgumentException("Only IAM event schema v2 is supported");
    Objects.requireNonNull(eventType, "eventType must not be null");
    Objects.requireNonNull(aggregateType, "aggregateType must not be null");
    Objects.requireNonNull(aggregateId, "aggregateId must not be null");
    Objects.requireNonNull(payload, "payload must not be null");
  }

  public record UserPayload(UUID userId, UUID actorUserId, Api.User before, Api.User after) {
    public UserPayload {
      Objects.requireNonNull(userId, "userId must not be null");
      Objects.requireNonNull(actorUserId, "actorUserId must not be null");
      Objects.requireNonNull(after, "after must not be null");
    }
  }

  public record RolePayload(UUID roleId, UUID actorUserId, Api.Role before, Api.Role after) {
    public RolePayload {
      Objects.requireNonNull(roleId, "roleId must not be null");
      Objects.requireNonNull(actorUserId, "actorUserId must not be null");
      Objects.requireNonNull(after, "after must not be null");
    }
  }
}
