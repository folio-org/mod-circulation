package org.folio.circulation.domain.events.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.vertx.core.json.JsonObject;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EntityChangedEventData (
  @JsonProperty("old") JsonObject oldVersion,
  @JsonProperty("new") JsonObject newVersion
) { }
