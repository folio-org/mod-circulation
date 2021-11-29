package org.folio.circulation.services;

import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TENANT_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TOKEN_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_URL_HEADER;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ClientErrorResponse;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.EventMetadata;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.util.pubsub.PubSubClientUtils;
import org.folio.util.pubsub.exceptions.EventSendingException;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;

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
    Event event = new Event()
      .withId(UUID.randomUUID().toString())
      .withEventType(eventType)
      .withEventPayload(payload)
      .withEventMetadata(new EventMetadata()
        .withPublishedBy(PubSubClientUtils.getModuleId())
        .withTenantId(okapiHeaders.get(OKAPI_TENANT_HEADER))
        .withEventTTL(1));

    final CompletableFuture<Boolean> publishResult = new CompletableFuture<>();
    OkapiConnectionParams params = new OkapiConnectionParams();
    params.setOkapiUrl(okapiHeaders.get(OKAPI_URL_HEADER));
    params.setTenantId(okapiHeaders.get(OKAPI_TENANT_HEADER));
    params.setToken(okapiHeaders.get(OKAPI_TOKEN_HEADER));

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
