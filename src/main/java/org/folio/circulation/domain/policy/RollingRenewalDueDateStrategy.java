package org.folio.circulation.domain.policy;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;

import java.util.function.Function;

import static org.folio.circulation.support.HttpResult.failure;

class RollingRenewalDueDateStrategy extends DueDateStrategy {
  private static final String RENEW_FROM_SYSTEM_DATE = "SYSTEM_DATE";
  private static final String RENEW_FROM_DUE_DATE = "CURRENT_DUE_DATE";

  private static final String NO_APPLICABLE_DUE_DATE_LIMIT_SCHEDULE_MESSAGE =
    "renewal date falls outside of the date ranges in the loan policy";

  private static final String RENEWAL_UNRECOGNISED_INTERVAL_MESSAGE =
    "the interval \"%s\" in the loan policy is not recognised";

  private static final String RENEWAL_INVALID_DURATION_MESSAGE =
    "the duration \"%s\" in the loan policy is invalid";

  private static final String RENEWAL_UNRECOGNISED_PERIOD_MESSAGE =
    "the loan period in the loan policy is not recognised";

  private static final String RENEW_FROM_UNRECOGNISED_MESSAGE =
    "cannot determine when to renew from";

  private final DateTime systemDate;
  private final String renewFrom;
  private final Period period;
  private final FixedDueDateSchedules dueDateLimitSchedules;
  private final Function<String, ValidationErrorFailure> error;

  RollingRenewalDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    DateTime systemDate,
    String renewFrom,
    Period period,
    FixedDueDateSchedules dueDateLimitSchedules) {

    super(loanPolicyId, loanPolicyName);
    this.systemDate = systemDate;
    this.renewFrom = renewFrom;
    this.period = period;
    this.dueDateLimitSchedules = dueDateLimitSchedules;

    error = this::validationError;
  }

  @Override
  HttpResult<DateTime> calculateDueDate(Loan loan) {
    switch (renewFrom) {
      case RENEW_FROM_DUE_DATE:
        return calculateDueDate(loan.getDueDate(), loan.getLoanDate());
      case RENEW_FROM_SYSTEM_DATE:
        return calculateDueDate(systemDate, loan.getLoanDate());
      default:
        return failure(error.apply(RENEW_FROM_UNRECOGNISED_MESSAGE));
    }
  }

  private HttpResult<DateTime> calculateDueDate(DateTime from, DateTime loanDate) {
    return renewalDueDate(from)
      .next(dueDate -> truncateDueDateBySchedule(loanDate, dueDate));
  }

  private HttpResult<DateTime> renewalDueDate(DateTime from) {
    return period.addTo(from,
      () -> error.apply(RENEWAL_UNRECOGNISED_PERIOD_MESSAGE),
      interval -> error.apply(String.format(RENEWAL_UNRECOGNISED_INTERVAL_MESSAGE, interval)),
      duration -> error.apply(String.format(RENEWAL_INVALID_DURATION_MESSAGE, duration)));
  }

  private HttpResult<DateTime> truncateDueDateBySchedule(
    DateTime loanDate,
    DateTime dueDate) {

    return dueDateLimitSchedules.truncateDueDate(dueDate, loanDate,
      () -> validationError(NO_APPLICABLE_DUE_DATE_LIMIT_SCHEDULE_MESSAGE));
  }
}
