package org.folio.circulation.services;

import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TENANT_HEADER;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.HttpStatus;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.rest.client.PubsubClient;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.EventMetadata;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.util.pubsub.PubSubClientUtils;
import org.folio.util.pubsub.exceptions.EventSendingException;

import io.vertx.core.Context;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

public class PubSubPublishingService {
  private static final Logger logger = LoggerFactory.getLogger(PubSubPublishingService.class);

  private final RoutingContext routingContext;
  private final Map<String, String> okapiHeaders;
  private final Context vertxContext;

  public PubSubPublishingService(RoutingContext routingContext) {
    this.routingContext = routingContext;
    okapiHeaders = new WebContext(routingContext).getHeaders();
    vertxContext = routingContext.vertx().getOrCreateContext();
  }

  public CompletableFuture<Boolean> publishEvent(String eventType, String payload) {
    Event event = new Event()
      .withId(UUID.randomUUID().toString())
      .withEventType(eventType)
      .withEventPayload(payload)
      .withEventMetadata(new EventMetadata()
        .withPublishedBy(PubSubClientUtils.constructModuleName())
        .withTenantId(okapiHeaders.get(OKAPI_TENANT_HEADER))
        .withEventTTL(1));

    OkapiConnectionParams params = new OkapiConnectionParams(okapiHeaders, vertxContext.owner());

    final CompletableFuture<Boolean> publishResult = new CompletableFuture<>();

    sendEventMessage(event, params)
      .whenComplete((result, throwable) -> {
        if (Boolean.TRUE.equals(result)) {
          logger.debug("Event published successfully. ID: {}, type: {}, payload: {}",
            event.getId(), event.getEventType(), event.getEventPayload());
          publishResult.complete(true);
        } else {
          logger.error("Failed to publish event. ID: {}, type: {}, payload: {}", throwable,
            event.getId(), event.getEventType(), event.getEventPayload());

          if (throwable != null && throwable.getMessage() != null &&
            throwable.getMessage().toLowerCase().contains(
            "there is no subscribers registered for event type")) {

            publishResult.complete(true);
          }
          else {
            publishResult.completeExceptionally(throwable);
          }
        }
      });

    return publishResult;
  }

  /**
   * Method that publishes event to PubSub and includes response body in the exception message in
   * case of any response status other than 204.
   *
   * @param eventMessage Event to be published
   * @param params Okapi connection parameters
   * @return
   */
  private CompletableFuture<Boolean> sendEventMessage(Event eventMessage,
    OkapiConnectionParams params) {

    PubsubClient client = new PubsubClient(params.getOkapiUrl(), params.getTenantId(),
      params.getToken());
    CompletableFuture<Boolean> result = new CompletableFuture<>();

    try {
      client.postPubsubPublish(eventMessage, (ar) -> {
        if (ar.statusCode() == HttpStatus.HTTP_NO_CONTENT.toInt()) {
          result.complete(true);
        } else {
          ar.bodyHandler(body -> {
            EventSendingException exception = new EventSendingException(String.format(
            "Error during publishing Event Message in PubSub. " +
              "Status code: %s . Status message: %s . Response body: %s",
            ar.statusCode(), ar.statusMessage(), body.toString()));
            logger.error(exception);
            result.completeExceptionally(exception);
          });
        }
      });
      return result;
    } catch (Exception var5) {
      logger.error("Error during sending event message to PubSub", var5);
      result.completeExceptionally(var5);
      return result;
    }
  }
}
