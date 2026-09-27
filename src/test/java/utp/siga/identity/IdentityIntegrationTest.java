package utp.siga.identity;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import utp.siga.identity.application.dto.Api;
import utp.siga.identity.application.usecase.AuthService;
import utp.siga.identity.infrastructure.security.Crypto;

@SpringBootTest(properties = {
        "iam.outbox-enabled=false",
        "iam.bootstrap-password=",
        "spring.data.redis.database=1"
})
@AutoConfigureMockMvc
class IdentityIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    Crypto crypto;
    @Autowired
    AuthService auth;
    @Autowired
    utp.siga.identity.infrastructure.persistence.IdentityRepository repository;
    @Autowired
    org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactionManager;
    static final String PASSWORD = "Test-only passphrase 123!";
    static final UUID ADMIN = UUID.fromString("12000000-0000-0000-0000-000000000001");
    String adminToken;
    String adminRefresh;

    @BeforeEach
    void reset() throws Exception {
        String db = jdbc.queryForObject("SELECT current_database()", String.class);
        assertThat(db).isIn("siga_identity_local_test", "siga_identity_test");
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("siga_iam_test");
        jdbc.execute(
                "TRUNCATE"
                        + " iam.outbox_event,iam.refresh_token,iam.user_role,iam.user_account,iam.role_permission,iam.role"
                        + " CASCADE");
        redis.execute(
                (org.springframework.data.redis.core.RedisCallback<Void>) c -> {
                    c.serverCommands().flushDb();
                    return null;
                });
        jdbc.update(
                "INSERT INTO iam.role(id,code,name) VALUES"
                        + " ('11000000-0000-0000-0000-000000000001','ADMIN','Administrador')");
        jdbc.update(
                "INSERT INTO iam.role_permission(role_id,permission_id) SELECT"
                        + " '11000000-0000-0000-0000-000000000001'::uuid,id FROM iam.permission");
        jdbc.update(
                "INSERT INTO iam.user_account(id,username,email,password_hash) VALUES"
                        + " (?,'admin.demo','admin@siga.test',?)",
                ADMIN,
                encoder.encode(PASSWORD));
        jdbc.update(
                "INSERT INTO iam.user_role(user_id,role_id) VALUES"
                        + " (?,'11000000-0000-0000-0000-000000000001')",
                ADMIN);
        var login = request(
                "POST",
                "/auth/login",
                Map.of("username", "admin.demo", "password", PASSWORD),
                null,
                200);
        assertThat(login.get("mfaRequired").asBoolean()).isTrue();
        assertThat(login.get("tokens").isNull()).isTrue();
        var enrollment = request(
                "POST",
                "/auth/mfa/enroll",
                Map.of("challengeId", login.get("challengeId").asText()),
                null,
                200);
        String otp = Crypto.totp(enrollment.get("secret").asText(), Instant.now().getEpochSecond() / 30);
        var adminTokens = request(
                "POST",
                "/auth/mfa/verify",
                Map.of("challengeId", login.get("challengeId").asText(), "otp", otp),
                null,
                200);
        adminToken = adminTokens.get("accessToken").asText();
        adminRefresh = adminTokens.get("refreshToken").asText();
    }

    JsonNode request(String method, String path, Object body, String token, int expected)
            throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), "/api/v1" + path)
                .contentType("application/json");
        if (body != null)
            req.content(json.writeValueAsBytes(body));
        if (token != null)
            req.header("Authorization", "Bearer " + token);
        var result = mvc.perform(req)
                .andExpect(status().is(expected))
                .andExpect(header().exists("X-Correlation-ID"))
                .andReturn();
        return result.getResponse().getContentAsString().isEmpty()
                ? json.nullNode()
                : json.readTree(result.getResponse().getContentAsString());
    }

    JsonNode createUser(String name, boolean mfa) throws Exception {
        return request(
                "POST",
                "/users",
                Map.of(
                        "username",
                        name,
                        "email",
                        name + "@siga.test",
                        "password",
                        PASSWORD,
                        "mfaEnabled",
                        mfa),
                adminToken,
                201);
    }

    JsonNode login(String name) throws Exception {
        return request(
                "POST", "/auth/login", Map.of("username", name, "password", PASSWORD), null, 200);
    }

    @Test
    void userLifecycleValidationRbacAndOutbox() throws Exception {
        request("GET", "/users", null, null, 401);
        var user = createUser("worker", false);
        String id = user.get("id").asText();
        request(
                "POST",
                "/users",
                Map.of("username", "worker", "email", "worker@siga.test", "password", PASSWORD),
                adminToken,
                409);
        request(
                "POST",
                "/users",
                Map.of("username", "bad", "email", "bad", "password", "short"),
                adminToken,
                400);
        request("GET", "/users?page=-1", null, adminToken, 400);
        request("GET", "/users/not-a-uuid", null, adminToken, 400);
        request("GET", "/users/" + UUID.randomUUID(), null, adminToken, 404);
        assertThat(request("GET", "/users", null, adminToken, 200).get("totalElements").asInt())
                .isEqualTo(2);
        String access = login("worker").get("tokens").get("accessToken").asText();
        request("GET", "/users", null, access, 403);
        request("GET", "/roles", null, access, 403);
        request(
                "PATCH",
                "/users/" + id,
                Map.of("email", "changed@siga.test", "active", false),
                adminToken,
                200);
        request("GET", "/users", null, access, 401);
        request("POST", "/auth/login", Map.of("username", "worker", "password", PASSWORD), null, 401);
        assertThat(
                jdbc.queryForObject(
                        "SELECT count(*) FROM iam.outbox_event WHERE aggregate_id=?",
                        Long.class,
                        UUID.fromString(id)))
                .isEqualTo(2);
        assertThat(
                jdbc.queryForObject(
                        "SELECT password_hash FROM iam.user_account WHERE id=?",
                        String.class,
                        UUID.fromString(id)))
                .startsWith("$argon2id$");
    }

    @Test
    void refreshRotationReuseLogoutAndJwtSignature() throws Exception {
        createUser("session", false);
        var tokens = login("session").get("tokens");
        String old = tokens.get("refreshToken").asText();
        var rotated = request("POST", "/auth/refresh", Map.of("refreshToken", old), null, 200);
        assertThat(rotated.get("refreshToken").asText()).isNotEqualTo(old);
        request("POST", "/auth/refresh", Map.of("refreshToken", old), null, 401);
        request(
                "POST",
                "/auth/refresh",
                Map.of("refreshToken", rotated.get("refreshToken").asText()),
                null,
                401);
        request("GET", "/users", null, rotated.get("accessToken").asText(), 401);
        var fresh = login("session").get("tokens");
        String access = fresh.get("accessToken").asText();
        request("GET", "/users", null, access.substring(0, access.lastIndexOf('.') + 1) + "AAAA", 401);
        request("POST", "/auth/logout", null, access, 204);
        request(
                "POST",
                "/auth/refresh",
                Map.of("refreshToken", fresh.get("refreshToken").asText()),
                null,
                401);
        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    @Test
    void mfaIsSingleUseEncryptedAndBounded() throws Exception {
        createUser("mfa", true);
        String challenge = login("mfa").get("challengeId").asText();
        String secret = request("POST", "/auth/mfa/enroll", Map.of("challengeId", challenge), null, 200)
                .get("secret")
                .asText();
        String stored = jdbc.queryForObject(
                "SELECT mfa_secret_encrypted FROM iam.user_account WHERE username='mfa'", String.class);
        assertThat(stored).doesNotContain(secret);
        String otp = Crypto.totp(secret, Instant.now().getEpochSecond() / 30);
        request("POST", "/auth/mfa/verify", Map.of("challengeId", challenge, "otp", otp), null, 200);
        request("POST", "/auth/mfa/verify", Map.of("challengeId", challenge, "otp", otp), null, 401);
        String next = login("mfa").get("challengeId").asText();
        request("POST", "/auth/mfa/enroll", Map.of("challengeId", next), null, 409);
        request("POST", "/auth/mfa/verify", Map.of("challengeId", next, "otp", otp), null, 401);
        for (int i = 0; i < 4; i++)
            request("POST", "/auth/mfa/verify", Map.of("challengeId", next, "otp", "000000"), null, 401);
        request("POST", "/auth/mfa/verify", Map.of("challengeId", next, "otp", "000000"), null, 429);
    }

    @Test
    void loginLockPersistsAcrossFailedTransactionsAndIpRateLimit() throws Exception {
        createUser("locked", false);
        for (int i = 0; i < 5; i++)
            request(
                    "POST",
                    "/auth/login",
                    Map.of("username", "locked", "password", "wrong-password"),
                    null,
                    401);
        request("POST", "/auth/login", Map.of("username", "locked", "password", PASSWORD), null, 429);
        assertThat(
                jdbc.queryForObject(
                        "SELECT failed_login_attempts FROM iam.user_account WHERE username='locked'",
                        Integer.class))
                .isEqualTo(5);
        jdbc.update(
                "UPDATE iam.user_account SET locked_until=now()-interval '1 minute' WHERE"
                        + " username='locked'");
        login("locked");
        for (int i = 0; i < 2; i++)
            request(
                    "POST", "/auth/login", Map.of("username", "unknown", "password", PASSWORD), null, 401);
        request("POST", "/auth/login", Map.of("username", "unknown", "password", PASSWORD), null, 429);
    }

    @Test
    void rolesPermissionsAndAssignmentsAreAtomic() throws Exception {
        var user = createUser("roles", false);
        String userId = user.get("id").asText();
        var role = request(
                "POST",
                "/roles",
                Map.of(
                        "code",
                        "READER",
                        "name",
                        "Reader",
                        "permissionIds",
                        List.of("10000000-0000-0000-0000-000000000001")),
                adminToken,
                201);
        String roleId = role.get("id").asText();
        request(
                "PUT",
                "/roles/" + roleId + "/permissions",
                Map.of("permissionIds", List.of(UUID.randomUUID().toString())),
                adminToken,
                404);
        assertThat(
                jdbc.queryForObject(
                        "SELECT count(*) FROM iam.role_permission WHERE role_id=?",
                        Long.class,
                        UUID.fromString(roleId)))
                .isEqualTo(1);
        request(
                "PUT", "/users/" + userId + "/roles", Map.of("roleIds", List.of(roleId)), adminToken, 200);
        request(
                "PUT",
                "/users/" + userId + "/roles",
                Map.of("roleIds", List.of(UUID.randomUUID().toString())),
                adminToken,
                404);
        assertThat(
                request("GET", "/users/" + userId, null, adminToken, 200)
                        .get("roleIds")
                        .get(0)
                        .asText())
                .isEqualTo(roleId);
        request(
                "PUT",
                "/roles/" + roleId + "/permissions",
                Map.of("permissionIds", List.of("10000000-0000-0000-0000-000000000009")),
                adminToken,
                200);
        assertThat(login("roles").get("mfaRequired").asBoolean()).isTrue();
        request("PATCH", "/users/" + userId, Map.of("mfaEnabled", false), adminToken, 409);
        request("GET", "/permissions", null, adminToken, 200);
    }

    @Test
    void concurrentRefreshAllowsOneRotationAndRevokesFamilyOnReuse() throws Exception {
        createUser("concurrent", false);
        String refresh = login("concurrent").get("tokens").get("refreshToken").asText();
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> operation = () -> {
                barrier.await();
                try {
                    auth.refresh(new Api.Refresh(refresh));
                    return 200;
                } catch (utp.siga.identity.domain.exception.IdentityException e) {
                    return e.status;
                }
            };
            var a = pool.submit(operation);
            var b = pool.submit(operation);
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 401);
        }
        assertThat(
                jdbc.queryForObject(
                        "SELECT count(*) FROM iam.refresh_token WHERE user_id=(SELECT id FROM"
                                + " iam.user_account WHERE username='concurrent') AND revoked_at IS NULL",
                        Long.class))
                .isZero();
    }

    @Test
    void outboxRetriesBrokerFailureAndPublishesCanonicalEnvelope() throws Exception {
        createUser("outbox", false);
        var broken = org.mockito.Mockito.mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
        org.mockito.Mockito.doThrow(new org.springframework.amqp.AmqpException("Simulated outage"))
                .when(broken)
                .send(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(org.springframework.amqp.core.Message.class),
                        org.mockito.ArgumentMatchers.any(
                                org.springframework.amqp.rabbit.connection.CorrelationData.class));
        var failedPublisher = new utp.siga.identity.infrastructure.messaging.OutboxPublisher(repository, broken, json);
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> failedPublisher.publish());
        assertThat(
                jdbc.queryForObject(
                        "SELECT count(*) FROM iam.outbox_event WHERE status='PENDING' AND attempts=1",
                        Long.class))
                .isEqualTo(1);
        var admin = new org.springframework.amqp.rabbit.core.RabbitAdmin(rabbit.getConnectionFactory());
        var exchange = new org.springframework.amqp.core.TopicExchange("siga.events", true, false);
        String queue = "siga.test." + UUID.randomUUID();
        admin.declareExchange(exchange);
        admin.declareQueue(new org.springframework.amqp.core.Queue(queue, false, true, true));
        admin.declareBinding(
                new org.springframework.amqp.core.Binding(
                        queue,
                        org.springframework.amqp.core.Binding.DestinationType.QUEUE,
                        "siga.events",
                        "iam.#",
                        null));
        try {
            jdbc.update("UPDATE iam.outbox_event SET next_attempt_at=now()");
            var publisher = new utp.siga.identity.infrastructure.messaging.OutboxPublisher(repository, rabbit, json);
            tx.executeWithoutResult(s -> publisher.publish());
            assertThat(
                    jdbc.queryForObject(
                            "SELECT count(*) FROM iam.outbox_event WHERE status='PUBLISHED'", Long.class))
                    .isEqualTo(1);
            var message = rabbit.receive(queue, 3000);
            assertThat(message).isNotNull();
            var envelope = json.readTree(message.getBody());
            assertThat(envelope.get("eventType").asText()).isEqualTo("UserCreated");
            assertThat(envelope.get("producer").asText()).isEqualTo("identity-service");
            assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        } finally {
            admin.deleteQueue(queue);
        }
    }

    @Test
    void protectedRoleAndSelfDeactivationAreRejected() throws Exception {
        request("PATCH", "/users/" + ADMIN, Map.of("active", false), adminToken, 409);
        request(
                "PUT",
                "/roles/11000000-0000-0000-0000-000000000001/permissions",
                Map.of("permissionIds", List.of()),
                adminToken,
                409);
    }

    @Test
    void refreshCannotRenewStepUpAndExpiredChallengesAreRejected() throws Exception {
        jdbc.update(
                "UPDATE iam.refresh_token SET mfa_authenticated_at=now()-interval '10 minutes' WHERE"
                        + " user_id=?",
                ADMIN);
        String token = request("POST", "/auth/refresh", Map.of("refreshToken", adminRefresh), null, 200)
                .get("accessToken")
                .asText();
        request("GET", "/roles", null, token, 200);
        request("POST", "/roles", Map.of("code", "STALE", "name", "Stale MFA"), token, 403);
        var challenge = login("admin.demo").get("challengeId").asText();
        redis.delete("iam:challenge:" + challenge);
        request(
                "POST", "/auth/mfa/verify", Map.of("challengeId", challenge, "otp", "000000"), null, 401);
    }
}
