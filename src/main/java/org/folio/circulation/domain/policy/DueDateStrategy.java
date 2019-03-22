package org.folio.circulation.domain.policy;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class DueDateStrategy {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final String loanPolicyId;
  private final String loanPolicyName;
  private final Function<String, ValidationError> errorForPolicy;

  DueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    Function<String, ValidationError> errorForPolicy) {

    this.loanPolicyId = loanPolicyId;
    this.loanPolicyName = loanPolicyName;
    this.errorForPolicy = errorForPolicy;
  }

  abstract HttpResult<DateTime> calculateDueDate(Loan loan);

  ValidationError validationError(String reason) {
    return errorForPolicy.apply(reason);
  }

  //TODO: Replace with logging in loan policy, that use toString of strategy
  void logApplying(String message) {
    log.info("Applying loan policy {} ({}): {}", loanPolicyName, loanPolicyId, message);
  }

  //TODO: Replace with exception handling in loan policy, that use toString of strategy
  void logException(Exception e, String message) {
    log.error("{}: {} ({})", message, loanPolicyName, loanPolicyId, e);
  }
}
