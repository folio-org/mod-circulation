package org.folio.circulation.domain.policy;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;

import java.util.function.Function;

class RollingRenewalDueDateStrategy extends DueDateStrategy {
  private static final String RENEWAL_UNRECOGNISED_INTERVAL_MESSAGE =
    "Item can't be renewed as the interval \"%s\" in the loan policy is not recognised.";

  private static final String RENEWAL_INVALID_DURATION_MESSAGE =
    "Item can't be renewed as the duration \"%s\" in the loan policy is invalid.";

  private static final String RENEWAL_UNRECOGNISED_PERIOD_MESSAGE =
    "Item can't be renewed as the loan period in the loan policy is not recognised.";

  private final Period period;
  private final Function<String, ValidationErrorFailure> error;

  RollingRenewalDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    String intervalId,
    Integer duration) {

    super(loanPolicyId, loanPolicyName);

    period = Period.from(duration, intervalId);

    error = this::validationError;
  }

  @Override
  HttpResult<DateTime> calculateDueDate(Loan loan, DateTime systemDate) {
    return period.addTo(systemDate,
      () -> error.apply(RENEWAL_UNRECOGNISED_PERIOD_MESSAGE),
      interval -> error.apply(String.format(RENEWAL_UNRECOGNISED_INTERVAL_MESSAGE, interval)),
      duration -> error.apply(String.format(RENEWAL_INVALID_DURATION_MESSAGE, duration)));
  }
}
