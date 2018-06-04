package org.folio.circulation.domain.policy;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;

import java.util.function.Function;

import static org.folio.circulation.support.HttpResult.failure;

class RollingCheckOutDueDateStrategy extends DueDateStrategy {
  private static final String NO_APPLICABLE_DUE_DATE_LIMIT_SCHEDULE_MESSAGE =
    "Item can't be checked out as the loan date falls outside of the date ranges in the loan policy.";

  private static final String CHECK_OUT_UNRECOGNISED_INTERVAL_MESSAGE =
    "Item can't be checked out as the interval \"%s\" in the loan policy is not recognised.";

  private static final String CHECKOUT_INVALID_DURATION_MESSAGE =
    "Item can't be checked out as the duration \"%s\" in the loan policy is invalid.";

  private static final String CHECK_OUT_UNRECOGNISED_PERIOD_MESSAGE =
    "Item can't be checked out as the loan period in the loan policy is not recognised.";

  private final FixedDueDateSchedules dueDateLimitSchedules;
  private final Period period;
  private final Function<String, ValidationErrorFailure> error;

  RollingCheckOutDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    FixedDueDateSchedules dueDateLimitSchedules,
    Period period) {

    super(loanPolicyId, loanPolicyName);
    this.period = period;
    this.dueDateLimitSchedules = dueDateLimitSchedules;

    error = this::validationError;
  }

  @Override
  HttpResult<DateTime> calculateDueDate(Loan loan) {
    final DateTime loanDate = loan.getLoanDate();

    return period.addTo(loanDate,
      () -> error.apply(CHECK_OUT_UNRECOGNISED_PERIOD_MESSAGE),
      interval -> error.apply(String.format(CHECK_OUT_UNRECOGNISED_INTERVAL_MESSAGE, interval)),
      duration -> error.apply(String.format(CHECKOUT_INVALID_DURATION_MESSAGE, duration)))
      .next(dueDate -> limitDueDateBySchedule(loanDate, dueDate));
  }

  private HttpResult<DateTime> limitDueDateBySchedule(
    DateTime loanDate,
    DateTime dueDate) {

    if(dueDateLimitSchedules != null) {
      return dueDateLimitSchedules.findDueDateFor(loanDate)
        .map(limit -> earliest(dueDate, limit))
        .map(HttpResult::success)
        .orElseGet(() -> failure(
          validationError(NO_APPLICABLE_DUE_DATE_LIMIT_SCHEDULE_MESSAGE)));
    }
    else {
      return HttpResult.success(dueDate);
    }
  }

  private DateTime earliest(DateTime rollingDueDate, DateTime limit) {
    return limit.isBefore(rollingDueDate)
      ? limit
      : rollingDueDate;
  }
}
