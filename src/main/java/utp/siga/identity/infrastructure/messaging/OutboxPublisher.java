package utp.siga.identity.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import utp.siga.identity.infrastructure.persistence.IdentityRepository;

@Component
@ConditionalOnProperty(name = "iam.outbox-enabled", havingValue = "true")
public class OutboxPublisher {
  private final IdentityRepository repo;
  private final RabbitTemplate rabbit;
  private final ObjectMapper mapper;

  public OutboxPublisher(IdentityRepository repo, RabbitTemplate rabbit, ObjectMapper mapper) {
    this.repo = repo;
    this.rabbit = rabbit;
    this.mapper = mapper;
  }

  @Bean
  TopicExchange identityExchange() {
    return new TopicExchange("siga.events", true, false);
  }

  @Bean
  Queue identityAuditQueue() {
    return new Queue("siga.identity.audit", true);
  }

  @Bean
  Binding identityBinding(Queue identityAuditQueue, TopicExchange identityExchange) {
    return BindingBuilder.bind(identityAuditQueue).to(identityExchange).with("iam.#");
  }

  @Scheduled(fixedDelayString = "${iam.outbox-delay:3000}")
  @Transactional
  public void publish() {
    var events = repo.jdbc()
        .queryForList(
            "SELECT * FROM iam.outbox_event WHERE status='PENDING' AND (next_attempt_at IS NULL"
                + " OR next_attempt_at<=now()) ORDER BY occurred_at LIMIT 20 FOR UPDATE SKIP"
                + " LOCKED");
    for (var row : events) {
      UUID id = (UUID) row.get("id");
      try {
        var envelope = envelope(row, mapper);
        var properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setMessageId(id.toString());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        var confirm = new CorrelationData(id.toString());
        rabbit.send(
            "siga.events",
            routingKey(row.get("event_type").toString(), ((Number) row.get("schema_version")).intValue()),
            new Message(mapper.writeValueAsBytes(envelope), properties),
            confirm);
        if (!confirm.getFuture().get(5, TimeUnit.SECONDS).isAck() || confirm.getReturned() != null)
          throw new IllegalStateException("Publish not confirmed");
        repo.jdbc()
            .update(
                "UPDATE iam.outbox_event SET"
                    + " status='PUBLISHED',published_at=now(),attempts=attempts+1 WHERE id=?",
                id);
      } catch (Exception e) {
        if (e instanceof InterruptedException)
          Thread.currentThread().interrupt();
        repo.jdbc()
            .update(
                "UPDATE iam.outbox_event SET attempts=attempts+1,next_attempt_at=now()+interval '30"
                    + " seconds' WHERE id=?",
                id);
      }
    }
  }

  static Map<String, Object> envelope(Map<String, Object> row, ObjectMapper mapper) throws Exception {
    var envelope = new LinkedHashMap<String, Object>();
    envelope.put("eventId", row.get("id"));
    envelope.put("eventType", row.get("event_type"));
    envelope.put("schemaVersion", row.get("schema_version"));
    envelope.put("producer", "identity-service");
    envelope.put("aggregateType", row.get("aggregate_type"));
    envelope.put("aggregateId", row.get("aggregate_id"));
    envelope.put("correlationId", row.get("correlation_id"));
    if (row.get("causation_id") != null) envelope.put("causationId", row.get("causation_id"));
    envelope.put(
        "occurredAt", ((java.sql.Timestamp) row.get("occurred_at")).toInstant().toString());
    envelope.put("payload", mapper.readTree(row.get("payload").toString()));
    return envelope;
  }

  static String routingKey(String eventType, int schemaVersion) {
    if (schemaVersion == 1) return "iam." + eventType;
    if (schemaVersion != 2) throw new IllegalArgumentException("Unsupported IAM schema version");
    return switch (eventType) {
      case "UserCreated" -> "iam.user.created.v2";
      case "UserUpdated" -> "iam.user.updated.v2";
      case "UserDisabled" -> "iam.user.disabled.v2";
      case "UserRolesChanged" -> "iam.user.roles.changed.v2";
      case "RoleCreated" -> "iam.role.created.v2";
      case "RolePermissionsChanged" -> "iam.role.permissions.changed.v2";
      default -> throw new IllegalArgumentException("Unsupported IAM v2 event type");
    };
  }
}
