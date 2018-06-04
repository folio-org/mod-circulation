package org.folio.circulation.domain.policy;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;

import java.util.function.Function;

class RollingRenewalDueDateStrategy extends DueDateStrategy {
  private static final String RENEW_FROM_SYSTEM_DATE = "SYSTEM_DATE";
  private static final String RENEW_FROM_DUE_DATE = "CURRENT_DUE_DATE";

  private static final String RENEWAL_UNRECOGNISED_INTERVAL_MESSAGE =
    "Item can't be renewed as the interval \"%s\" in the loan policy is not recognised.";

  private static final String RENEWAL_INVALID_DURATION_MESSAGE =
    "Item can't be renewed as the duration \"%s\" in the loan policy is invalid.";

  private static final String RENEWAL_UNRECOGNISED_PERIOD_MESSAGE =
    "Item can't be renewed as the loan period in the loan policy is not recognised.";

  private static final String RENEW_FROM_UNRECOGNISED_MESSAGE =
    "Item can't be renewed as cannot determine when to renew from.";

  private final Period period;
  private final Function<String, ValidationErrorFailure> error;
  private final DateTime systemDate;
  private final String renewFrom;

  RollingRenewalDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    String intervalId,
    Integer duration,
    DateTime systemDate,
    String renewFrom) {

    super(loanPolicyId, loanPolicyName);
    this.systemDate = systemDate;
    this.renewFrom = renewFrom;

    period = Period.from(duration, intervalId);

    error = this::validationError;
  }

  @Override
  HttpResult<DateTime> calculateDueDate(Loan loan) {
    switch (renewFrom) {
      case RENEW_FROM_DUE_DATE:
        return calculateDueDate(loan.getDueDate());
      case RENEW_FROM_SYSTEM_DATE:
        return calculateDueDate(systemDate);
      default:
        return HttpResult.failure(error.apply(RENEW_FROM_UNRECOGNISED_MESSAGE));
    }
  }

  private HttpResult<DateTime> calculateDueDate(DateTime from) {
    return period.addTo(from,
      () -> error.apply(RENEWAL_UNRECOGNISED_PERIOD_MESSAGE),
      interval -> error.apply(String.format(RENEWAL_UNRECOGNISED_INTERVAL_MESSAGE, interval)),
      duration -> error.apply(String.format(RENEWAL_INVALID_DURATION_MESSAGE, duration)));
  }
}
