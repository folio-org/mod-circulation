package org.folio.circulation.services;

import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.folio.circulation.domain.events.CirculationKafkaTopic.fromEventType;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.services.events.KafkaService;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.Context;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class KafkaEventPublishingService implements EventPublishingService {
  private final Map<String, String> okapiHeaders;
  private final String tenantId;
  private final Context vertxContext;
  private final KafkaService kafkaService;

  public KafkaEventPublishingService(WebContext context) {
    this(context.getHeaders(), context.getVertxContext());
  }

  KafkaEventPublishingService(Map<String, String> okapiHeaders, Context vertxContext) {
    this(okapiHeaders, vertxContext,
      new KafkaService(requireVertxContext(vertxContext).owner()));
  }

  KafkaEventPublishingService(Map<String, String> okapiHeaders, Context vertxContext,
    KafkaService kafkaService) {

    if (vertxContext == null) {
      throw new IllegalStateException("Kafka event publishing requires a Vert.x context");
    }

    this.okapiHeaders = okapiHeaders;
    this.tenantId = tenantIdFrom(okapiHeaders);
    this.vertxContext = vertxContext;
    this.kafkaService = requireNonNull(kafkaService, "Kafka service is required");
  }

  @Override
  public CompletableFuture<Boolean> publishEvent(String eventType, String payload) {
    log.info("publishEvent:: eventType={}, tenantId={}", eventType, tenantId);

    return fromEventType(eventType)
      .map(topic -> kafkaService.createPublisher(topic, vertxContext, tenantId)
        .publish(randomUUID().toString(), payload, okapiHeaders)
        .thenApply(KafkaEventPublishingService::resultSucceeded))
      .orElseGet(() -> failedFuture(new IllegalArgumentException(
        "Unsupported circulation Kafka event type: " + eventType)));
  }

  private static boolean resultSucceeded(Result<?> result) {
    return result.succeeded();
  }

  private static String tenantIdFrom(Map<String, String> okapiHeaders) {
    return okapiHeaders.entrySet().stream()
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
