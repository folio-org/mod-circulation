package org.folio.circulation.domain.policy;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

abstract class DueDateStrategy {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  //TODO: Move failure and logging external to calculation, as could be used in multiple contexts
  private final String loanPolicyId;
  private final String loanPolicyName;

  DueDateStrategy(String loanPolicyId, String loanPolicyName) {
    this.loanPolicyId = loanPolicyId;
    this.loanPolicyName = loanPolicyName;
  }

  abstract HttpResult<DateTime> calculateDueDate(Loan loan);

  <T> HttpResult<T> fail(String reason) {
    log.error(reason);
    return HttpResult.failure(validationError(reason));
  }

  ValidationErrorFailure validationError(String reason) {
    return ValidationErrorFailure.error(
      String.format(
        "%s Please review \"%s\" before retrying", reason, loanPolicyName),
      "loanPolicyId", this.loanPolicyId);
  }

  void logApplying(String message) {
    log.info("Applying loan policy {} ({}): {}", loanPolicyName, loanPolicyId, message);
  }

  void logException(Exception e, String message) {
    log.error("{}: {} ({})", message, loanPolicyName, loanPolicyId, e);
  }
}
