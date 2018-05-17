package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;

class FixedDueDateSchedules extends JsonObject {
  FixedDueDateSchedules(JsonObject representation) {
    super(representation.getMap());
  }
}
