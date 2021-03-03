package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.allOf;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.EventType;
import org.folio.rest.client.PubsubClient;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.util.pubsub.PubSubClientUtils;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class PubSubRegistrationService {
  private static final Logger logger = LoggerFactory.getLogger(PubSubRegistrationService.class);

  private PubSubRegistrationService() {
    throw new IllegalStateException();
  }

  public static CompletableFuture<Boolean> registerModule(
    Map<String, String> headers, Vertx vertx) {

    return PubSubClientUtils.registerModule(new OkapiConnectionParams(headers, vertx))
      .whenComplete((registrationAr, throwable) -> {
        if (throwable == null) {
          logger.info("Module was successfully registered as publisher/subscriber in mod-pubsub");
        } else {
          logger.error("Error during module registration in mod-pubsub", throwable);
        }
      });
  }

  public static CompletableFuture<Boolean> unregisterModule(
    Map<String, String> headers, Vertx vertx) {

    List<CompletableFuture<Boolean>> list = new ArrayList<>();

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);
    PubsubClient client = new PubsubClient(params.getOkapiUrl(), params.getTenantId(),
      params.getToken());

    try {
      for (EventType eventType : EventType.values()) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        client.deletePubsubEventTypesPublishersByEventTypeName(eventType.name(),
          PubSubClientUtils.constructModuleName(), ar -> {
            if (ar.succeeded()) {
              int statusCode = ar.result().statusCode();
              if (statusCode == HTTP_NO_CONTENT.toInt()) {
                future.complete(true);
              } else {
                String errorMessage = String.format(
                  "Failed to unregister publisher for event type %s in PubSub. HTTP status: %d",
                  eventType.name(), statusCode);
                logger.error(errorMessage);
                future.completeExceptionally(new ModulePubSubUnregisteringException(errorMessage));
              }
            } else {
              String errorMessage = String.format(
                "Failed to unregister publisher for event type %s in PubSub. Cause: %s",
                eventType.name(), ar.cause().getMessage());
              logger.error(errorMessage);
              future.completeExceptionally(
                new ModulePubSubUnregisteringException(errorMessage, ar.cause()));
            }
          });
        list.add(future);
      }
    } catch (Exception exception) {
      logger.error("Module's publishers were not unregistered from PubSub.", exception);
    }

    return allOf(list.toArray(new CompletableFuture[0])).thenApply(r -> true);
  }
}
