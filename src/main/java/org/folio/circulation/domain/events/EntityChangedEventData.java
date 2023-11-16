package org.folio.circulation.domain.events;

import io.vertx.core.json.JsonObject;

public record EntityChangedEventData (JsonObject oldVersion, JsonObject newVersion) { }
