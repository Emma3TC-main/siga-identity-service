package utp.siga.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import utp.siga.identity.application.dto.Api;
import utp.siga.identity.application.event.IdentityAuditEvent;
import utp.siga.identity.application.port.out.AuthenticatedActorPort;
import utp.siga.identity.application.port.out.CredentialPort;
import utp.siga.identity.application.port.out.CryptoPort;
import utp.siga.identity.application.port.out.IdentityEventPort;
import utp.siga.identity.application.port.out.ManagementPersistencePort;

@ExtendWith(MockitoExtension.class)
class ManagementServiceIdentityAuditTest {
  private static final UUID ACTOR_ID = UUID.fromString("12000000-0000-0000-0000-000000000001");

  @Mock private ManagementPersistencePort repo;
  @Mock private IdentityEventPort events;
  @Mock private AuthenticatedActorPort actor;
  @Mock private CredentialPort passwords;
  @Mock private CryptoPort crypto;

  private ManagementService service;

  @BeforeEach
  void setUp() {
    lenient().when(actor.currentUserId()).thenReturn(ACTOR_ID);
    service = new ManagementService(repo, events, actor, passwords, crypto);
  }

  @Test
  void emitsUserCreatedV2WithSafeAfterSnapshot() {
    UUID roleId = UUID.randomUUID();
    when(repo.role(roleId)).thenReturn(role(roleId, "USER", Set.of()));
    when(passwords.encode("safe-password")).thenReturn("hash");
    when(repo.user(any())).thenAnswer(invocation -> user(invocation.getArgument(0), true, Set.of(roleId)));

    service.create(new Api.CreateUser("user", "user@siga.test", "safe-password", false, Set.of(roleId)));

    IdentityAuditEvent event = capturedEvent();
    assertEvent(event, "UserCreated", "User");
    var payload = (IdentityAuditEvent.UserPayload) event.payload();
    assertThat(payload.actorUserId()).isEqualTo(ACTOR_ID);
    assertThat(payload.before()).isNull();
    assertThat(payload.after().id()).isEqualTo(event.aggregateId());
  }

  @Test
  void emitsUserUpdatedAndUserDisabledOnlyForEffectiveChanges() {
    UUID userId = UUID.randomUUID();
    Api.User before = user(userId, true, Set.of());
    Api.User updated = new Api.User(userId, "user", "updated@siga.test", true, false, Set.of());
    Api.User disabled = new Api.User(userId, "user", "updated@siga.test", false, false, Set.of());
    when(repo.user(userId)).thenReturn(before, updated, updated, disabled);

    service.update(userId, new Api.UpdateUser("updated@siga.test", null, null));
    service.update(userId, new Api.UpdateUser(null, false, null));

    var captor = ArgumentCaptor.forClass(IdentityAuditEvent.class);
    verify(events, times(2)).append(captor.capture());
    assertThat(captor.getAllValues()).extracting(IdentityAuditEvent::eventType)
        .containsExactly("UserUpdated", "UserDisabled");
    var disabledPayload = (IdentityAuditEvent.UserPayload) captor.getAllValues().get(1).payload();
    assertThat(disabledPayload.before().active()).isTrue();
    assertThat(disabledPayload.after().active()).isFalse();
  }

  @Test
  void emitsUserRolesChangedWithCompleteSnapshotsAndTreatsOrderAsNoOp() {
    UUID userId = UUID.randomUUID();
    UUID roleA = UUID.randomUUID();
    UUID roleB = UUID.randomUUID();
    Api.User before = user(userId, true, Set.of(roleA));
    Api.User after = user(userId, true, Set.of(roleA, roleB));
    when(repo.user(userId)).thenReturn(before, after, after);
    when(repo.role(any())).thenAnswer(invocation -> role(invocation.getArgument(0), "ROLE", Set.of()));

    service.assignRoles(userId, new Api.AssignRoles(Set.of(roleA, roleB)));
    IdentityAuditEvent event = capturedEvent();
    assertEvent(event, "UserRolesChanged", "User");
    var payload = (IdentityAuditEvent.UserPayload) event.payload();
    assertThat(payload.before().roleIds()).containsExactly(roleA);
    assertThat(payload.after().roleIds()).containsExactlyInAnyOrder(roleA, roleB);

    clearInvocations(events);
    service.assignRoles(userId, new Api.AssignRoles(Set.of(roleB, roleA)));
    verifyNoInteractions(events);
  }

  @Test
  void emitsRoleCreatedAndRolePermissionsChangedAndSuppressesNoOp() {
    UUID permissionA = UUID.randomUUID();
    UUID permissionB = UUID.randomUUID();
    when(repo.permissionExists(any())).thenReturn(true);
    when(repo.role(any())).thenAnswer(invocation -> role(invocation.getArgument(0), "WAREHOUSE", Set.of(permissionA)));

    service.createRole(new Api.CreateRole("WAREHOUSE", "Warehouse", Set.of(permissionA)));
    IdentityAuditEvent created = capturedEvent();
    assertEvent(created, "RoleCreated", "Role");

    clearInvocations(events);
    UUID roleId = UUID.randomUUID();
    Api.Role before = role(roleId, "WAREHOUSE", Set.of(permissionA));
    Api.Role after = role(roleId, "WAREHOUSE", Set.of(permissionA, permissionB));
    when(repo.role(roleId)).thenReturn(before, after, after);
    service.assignPermissions(roleId, new Api.AssignPermissions(Set.of(permissionA, permissionB)));
    IdentityAuditEvent changed = capturedEvent();
    assertEvent(changed, "RolePermissionsChanged", "Role");

    clearInvocations(events);
    service.assignPermissions(roleId, new Api.AssignPermissions(Set.of(permissionB, permissionA)));
    verifyNoInteractions(events);
  }

  @Test
  void suppressesNoOpUserUpdateWithoutCredentialOrOutboxWrites() {
    UUID userId = UUID.randomUUID();
    when(repo.user(userId)).thenReturn(user(userId, true, Set.of()));

    service.update(userId, new Api.UpdateUser("user@siga.test", true, false));

    verify(repo, never()).updateEmail(any(), any());
    verify(repo, never()).updateActive(any(), anyBoolean());
    verify(repo, never()).updateMfa(any(), anyBoolean(), any());
    verify(repo, never()).revokeUser(any());
    verifyNoInteractions(events);
  }

  private IdentityAuditEvent capturedEvent() {
    var captor = ArgumentCaptor.forClass(IdentityAuditEvent.class);
    verify(events).append(captor.capture());
    return captor.getValue();
  }

  private static void assertEvent(IdentityAuditEvent event, String type, String aggregateType) {
    assertThat(event.schemaVersion()).isEqualTo(2);
    assertThat(event.eventType()).isEqualTo(type);
    assertThat(event.aggregateType()).isEqualTo(aggregateType);
  }

  private static Api.User user(UUID id, boolean active, Set<UUID> roles) {
    return new Api.User(id, "user", "user@siga.test", active, false, roles);
  }

  private static Api.Role role(UUID id, String code, Set<UUID> permissions) {
    return new Api.Role(id, code, "Role", true, permissions);
  }
}
