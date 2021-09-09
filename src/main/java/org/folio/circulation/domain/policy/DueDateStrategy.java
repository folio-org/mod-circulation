package org.folio.circulation.domain.policy;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

public abstract class DueDateStrategy {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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

  public abstract Result<ZonedDateTime> calculateDueDate(Loan loan);

  ValidationError errorForPolicy(String reason) {
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
