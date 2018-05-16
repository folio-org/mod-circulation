package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class LoanPolicy extends JsonObject {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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

  public HttpResult<DateTime> calculate(JsonObject loan) {
    return new DueDateStrategy().calculate(loan, this);
  }

  LoanPolicy withDueDateSchedule(JsonObject fixedDueDateSchedules) {
    return new LoanPolicy(this, fixedDueDateSchedules);
  }
}
