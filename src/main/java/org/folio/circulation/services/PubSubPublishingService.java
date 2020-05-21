package org.folio.circulation.services;

import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TENANT_HEADER;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.EventMetadata;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.util.pubsub.PubSubClientUtils;

import io.vertx.core.Context;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

public class PubSubPublishingService {
  private static final Logger logger = LoggerFactory.getLogger(PubSubPublishingService.class);

  private final Map<String, String> okapiHeaders;
  private final Context vertxContext;

  public PubSubPublishingService(RoutingContext routingContext) {
    okapiHeaders = routingContext.request().headers().entries().stream()
      .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(), Map.Entry::getValue));
    vertxContext = routingContext.vertx().getOrCreateContext();
  }

  public void publishEvent(String eventType, String payload) {
    Event event = new Event()
      .withId(UUID.randomUUID().toString())
      .withEventType(eventType)
      .withEventPayload(payload)
      .withEventMetadata(new EventMetadata()
        .withPublishedBy(PubSubClientUtils.constructModuleName())
        .withTenantId(okapiHeaders.get(OKAPI_TENANT_HEADER))
        .withEventTTL(1));

    OkapiConnectionParams params = new OkapiConnectionParams(okapiHeaders, vertxContext.owner());

    PubSubClientUtils.sendEventMessage(event, params)
      .whenComplete((result, throwable) -> {
        if (Boolean.TRUE.equals(result)) {
          logger.debug("Event published successfully. ID: {}, type: {}, payload: {}",
            event.getId(), event.getEventType(), event.getEventPayload());
        } else {
          logger.error("Failed to publish event. ID: {}, type: {}, payload: {}", throwable,
            event.getId(), event.getEventType(), event.getEventPayload());
        }
      });
  }
}
