package org.folio.circulation.services;

import java.util.concurrent.CompletableFuture;

import io.vertx.core.json.JsonObject;

public interface EventPublishingService {
  CompletableFuture<Void> publishEvent(String key, String eventType, JsonObject payload);
}
