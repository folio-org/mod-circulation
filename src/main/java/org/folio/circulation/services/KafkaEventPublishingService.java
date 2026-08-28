package org.folio.circulation.services;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.folio.circulation.domain.events.CirculationKafkaTopic.fromEventType;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.services.events.KafkaService;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class KafkaEventPublishingService implements EventPublishingService {
  private final Map<String, String> okapiHeaders;
  private final Context vertxContext;
  private final KafkaService kafkaService;

  public KafkaEventPublishingService(WebContext context) {
    this(context.getHeaders(), Vertx.currentContext());
  }

  KafkaEventPublishingService(Map<String, String> okapiHeaders, Context vertxContext) {
    if (vertxContext == null) {
      throw new IllegalStateException("Kafka event publishing requires a Vert.x context");
    }

    this.okapiHeaders = okapiHeaders;
    this.vertxContext = vertxContext;
    this.kafkaService = new KafkaService(vertxContext.owner());
  }

  @Override
  public CompletableFuture<Boolean> publishEvent(String eventType, String payload) {
    log.info("publishEvent:: eventType={}, tenantId={}", eventType, tenantId(okapiHeaders));

    return fromEventType(eventType)
      .map(topic -> kafkaService.createPublisher(topic, vertxContext, tenantId(okapiHeaders))
        .publish(randomUUID().toString(), payload, okapiHeaders)
        .thenApply(result -> result.succeeded()))
      .orElseGet(() -> failedFuture(new IllegalArgumentException(
        "Unsupported circulation Kafka event type: " + eventType)));
  }
}
