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

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class PubSubRegistrationService {
  private static final Logger logger = LoggerFactory.getLogger(PubSubRegistrationService.class);

  public static void registerModule(Map<String, String> headers, Vertx vertx,
    Promise<Object> promise) {

    PubSubClientUtils.registerModule(new OkapiConnectionParams(headers, vertx))
      .whenComplete((registrationAr, throwable) -> {
        if (throwable == null) {
          logger.info("Module was successfully registered as publisher/subscriber in mod-pubsub");
          promise.complete();
        } else {
          logger.error("Error during module registration in mod-pubsub", throwable);
          promise.fail(throwable);
        }
      });
  }

  public static void unregisterModule(Map<String, String> headers, Vertx vertx,
    Promise<Object> promise) {

    List<CompletableFuture<Boolean>> list = new ArrayList<>();

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);
    PubsubClient client = new PubsubClient(params.getOkapiUrl(), params.getTenantId(),
      params.getToken());

    try {
      for (EventType eventType: EventType.values()) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        client.deletePubsubEventTypesPublishersByEventTypeName(eventType.name(),
          PubSubClientUtils.constructModuleName(), ar -> {
            if (ar.statusCode() == HTTP_NO_CONTENT.toInt()) {
              future.complete(true);
            } else {
              ModulePubSubUnregisteringException exception =
                new ModulePubSubUnregisteringException(String.format("Module's publisher for " +
                  "event type %s was not unregistered from PubSub. HTTP status: %s",
                  eventType.name(), ar.statusCode()));
              logger.error(exception);
              promise.fail(exception);
            }
          });
        list.add(future);
      }

    } catch (Exception exception) {
      logger.error("Module's publishers were not unregistered from PubSub.", exception);
      promise.fail(exception);
    }

    allOf(list.toArray(new CompletableFuture[0])).thenRun(promise::complete);
  }
}
