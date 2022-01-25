package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public interface MappableToJson {
  JsonObject toJson();
}
