package utp.siga.identity.infrastructure;

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
    var events =
        repo.jdbc()
            .queryForList(
                "SELECT * FROM iam.outbox_event WHERE status='PENDING' AND (next_attempt_at IS NULL"
                    + " OR next_attempt_at<=now()) ORDER BY occurred_at LIMIT 20 FOR UPDATE SKIP"
                    + " LOCKED");
    for (var row : events) {
      UUID id = (UUID) row.get("id");
      try {
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", id);
        envelope.put("eventType", row.get("event_type"));
        envelope.put("schemaVersion", row.get("schema_version"));
        envelope.put("producer", "identity-service");
        envelope.put("aggregateType", row.get("aggregate_type"));
        envelope.put("causationId", row.get("causation_id"));
        envelope.put("aggregateId", row.get("aggregate_id"));
        envelope.put("correlationId", row.get("correlation_id"));
        envelope.put(
            "occurredAt", ((java.sql.Timestamp) row.get("occurred_at")).toInstant().toString());
        envelope.put("payload", mapper.readTree(row.get("payload").toString()));
        var properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setMessageId(id.toString());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        var confirm = new CorrelationData(id.toString());
        rabbit.send(
            "siga.events",
            "iam." + row.get("event_type").toString(),
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
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        repo.jdbc()
            .update(
                "UPDATE iam.outbox_event SET attempts=attempts+1,next_attempt_at=now()+interval '30"
                    + " seconds' WHERE id=?",
                id);
      }
    }
  }
}
