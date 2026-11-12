package org.folio.circulation.services.events;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;

@FunctionalInterface
interface KafkaEventProcessor {
  CompletableFuture<Result<Void>> process(JsonObject eventPayload, WebContext context,
    HttpClient client);
}
