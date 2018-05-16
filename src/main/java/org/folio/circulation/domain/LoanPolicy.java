package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class LoanPolicy extends JsonObject {
  final JsonObject fixedDueDateSchedules;

  private LoanPolicy(JsonObject representation) {
    this(representation, null);
  }

  LoanPolicy(JsonObject representation, JsonObject fixedDueDateSchedules) {
    super(representation.getMap());

    this.fixedDueDateSchedules = fixedDueDateSchedules;
  }

  static LoanPolicy from(JsonObject representation) {
    return new LoanPolicy(representation);
  }

  LoanPolicy withDueDateSchedule(JsonObject fixedDueDateSchedules) {
    return new LoanPolicy(this, fixedDueDateSchedules);
  }
}
