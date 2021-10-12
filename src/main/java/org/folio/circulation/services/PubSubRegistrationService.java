package org.folio.circulation.services;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.util.pubsub.PubSubClientUtils;

import io.vertx.core.Vertx;

public class PubSubRegistrationService {
  private static final Logger logger = LogManager.getLogger(PubSubRegistrationService.class);

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
}
