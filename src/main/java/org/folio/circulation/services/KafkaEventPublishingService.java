package org.folio.circulation.services;

import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.folio.circulation.domain.events.AuditKafkaTopic;
import org.folio.circulation.domain.events.CirculationKafkaTopic;
import org.folio.circulation.domain.events.KafkaTopicDefinition;
import org.folio.circulation.services.events.KafkaService;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class KafkaEventPublishingService implements EventPublishingService {
  private static final List<KafkaTopicDefinition> EVENT_TOPICS =
    Stream.<KafkaTopicDefinition>concat(
      Arrays.stream(CirculationKafkaTopic.values()),
      Arrays.stream(AuditKafkaTopic.values()))
    .toList();

  private final Map<String, String> headers;
  private final String tenantId;
  private final Context vertxContext;
  private final KafkaService kafkaService;

  public KafkaEventPublishingService(WebContext context) {
    this(context.getHeaders(), context.getVertxContext());
  }

  KafkaEventPublishingService(Map<String, String> headers, Context vertxContext) {
    this(headers, vertxContext,
      new KafkaService(requireVertxContext(vertxContext).owner()));
  }

  KafkaEventPublishingService(Map<String, String> headers, Context vertxContext,
    KafkaService kafkaService) {

    if (vertxContext == null) {
      throw new IllegalStateException("Kafka event publishing requires a Vert.x context");
    }

    this.headers = headers;
    this.tenantId = tenantIdFrom(headers);
    this.vertxContext = vertxContext;
    this.kafkaService = requireNonNull(kafkaService, "Kafka service is required");
  }

  @Override
  public CompletableFuture<Void> publishEvent(String eventType, JsonObject payload) {
    log.info("publishEvent:: eventType={}, tenantId={}", eventType, tenantId);

    return EVENT_TOPICS.stream()
      .filter(topic -> topic.topicName().equals(eventType))
      .findFirst()
      .map(topic -> kafkaService.createPublisher(topic, vertxContext, tenantId)
        .publish(randomUUID().toString(), payload, headers)
        .thenApply(KafkaEventPublishingService::failOnPublishingError))
      .orElseGet(() -> failedFuture(new IllegalArgumentException(
        "Unsupported Kafka event type: " + eventType)));
  }

  private static Void failOnPublishingError(Result<?> result) {
    if (result.failed()) {
      throw new IllegalStateException(result.cause().toString());
    }

    return null;
  }

  private static String tenantIdFrom(Map<String, String> headers) {
    return headers.entrySet().stream()
      .filter(entry -> TENANT.equalsIgnoreCase(entry.getKey()))
      .map(Map.Entry::getValue)
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException(TENANT + " header is required"));
  }

  private static Context requireVertxContext(Context vertxContext) {
    if (vertxContext == null) {
      throw new IllegalStateException("Kafka event publishing requires a Vert.x context");
    }

    return vertxContext;
  }
}
