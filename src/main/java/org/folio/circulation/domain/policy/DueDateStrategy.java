package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

abstract class DueDateStrategy {
  static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  //TODO: Remove, as this is a slight bleed from the calling context (loan policy)
  protected final String loanPolicyId;
  private final String loanPolicyName;

  DueDateStrategy(String loanPolicyId, String loanPolicyName) {
    this.loanPolicyId = loanPolicyId;
    this.loanPolicyName = loanPolicyName;
  }

  abstract HttpResult<DateTime> calculate(JsonObject loan);

  HttpResult<DateTime> fail(String reason) {
    final String message = String.format(
      "%s Please review %s before retrying checking out", reason, loanPolicyName);

    log.warn(message);

    return HttpResult.failure(new ValidationErrorFailure(
      message, "loanPolicyId", this.loanPolicyId));
  }
}
