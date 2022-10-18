package org.folio.circulation.services;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.EventMetadata;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.util.pubsub.PubSubClientUtils;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;

public class PubSubPublishingService {
  private static final Logger logger = LogManager.getLogger(PubSubPublishingService.class);

  private final Map<String, String> okapiHeaders;
  private final Context vertxContext;

  public PubSubPublishingService(RoutingContext routingContext) {
    this(new WebContext(routingContext));
  }

  public PubSubPublishingService(WebContext context) {
    this.okapiHeaders = context.getHeaders();
    vertxContext = Vertx.currentContext();
  }

  public CompletableFuture<Boolean> publishEvent(String eventType, String payload) {
    OkapiConnectionParams params = new OkapiConnectionParams(okapiHeaders, vertxContext.owner());
    Event event = new Event()
      .withId(UUID.randomUUID().toString())
      .withEventType(eventType)
      .withEventPayload(payload)
      .withEventMetadata(new EventMetadata()
        .withPublishedBy(PubSubClientUtils.getModuleId())
        .withTenantId(params.getTenantId())
        .withEventTTL(1));

    final CompletableFuture<Boolean> publishResult = new CompletableFuture<>();

    vertxContext.runOnContext(v -> PubSubClientUtils.sendEventMessage(event, params)
      .whenComplete((result, throwable) -> {
        if (Boolean.TRUE.equals(result)) {
          logger.info("Event published successfully. ID: {}, type: {}, payload: {}",
            event.getId(), event.getEventType(), event.getEventPayload());
          publishResult.complete(true);
        } else {
          logger.error("Failed to publish event. ID: {}, type: {}, payload: {}, cause: {}",
            event.getId(), event.getEventType(), event.getEventPayload(), throwable);
          if (throwable == null) {
            publishResult.complete(false);
          } else {
            publishResult.completeExceptionally(throwable);
          }
        }
      })
    );

    return publishResult;
  }
}
