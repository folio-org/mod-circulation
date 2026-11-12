package org.folio.circulation.domain.policy;

import java.time.ZonedDateTime;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.ErrorCode;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

public abstract class DueDateStrategy {

  protected final String loanPolicyId;
  protected final String loanPolicyName;
  private final BiFunction<String, ErrorCode, ValidationError> errorForPolicy;

  DueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    Function<String, ValidationError> errorForPolicy) {

    this(loanPolicyId, loanPolicyName, (message, errorCode) -> errorForPolicy.apply(message));
  }

  DueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    BiFunction<String, ErrorCode, ValidationError> errorForPolicy) {

    this.loanPolicyId = loanPolicyId;
    this.loanPolicyName = loanPolicyName;
    this.errorForPolicy = errorForPolicy;
  }

  public abstract Result<ZonedDateTime> calculateDueDate(Loan loan);

  ValidationError errorForPolicy(String reason) {
    return errorForPolicy(reason, null);
  }

  ValidationError errorForPolicy(String reason, ErrorCode errorCode) {
    return errorForPolicy.apply(reason, errorCode);
  }
}
