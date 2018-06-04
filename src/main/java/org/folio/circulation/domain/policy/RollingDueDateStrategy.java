package org.folio.circulation.domain.policy;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;

import java.util.function.Function;

import static org.folio.circulation.support.HttpResult.failure;

class RollingDueDateStrategy extends DueDateStrategy {
  private static final String NO_APPLICABLE_DUE_DATE_LIMIT_SCHEDULE_MESSAGE =
    "Item can't be checked out as the loan date falls outside of the date ranges in the loan policy.";

  private static final String UNRECOGNISED_INTERVAL_MESSAGE =
    "Item can't be checked out as the interval \"%s\" in the loan policy is not recognised.";

  private static final String INVALID_DURATION_MESSAGE =
    "Item can't be checked out as the duration \"%s\" in the loan policy is invalid.";

  private static final String UNRECOGNISED_PERIOD_MESSAGE =
    "Item can't be checked out as the loan period in the loan policy is not recognised.";

  private final String intervalId;
  private final Integer duration;
  private final FixedDueDateSchedules dueDateLimitSchedules;
  private final Period period;
  private final Function<String, ValidationErrorFailure> loanPolicyRelatedError;

  RollingDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    String intervalId,
    Integer duration,
    FixedDueDateSchedules dueDateLimitSchedules) {

    super(loanPolicyId, loanPolicyName);
    this.intervalId = intervalId;
    this.duration = duration;
    this.dueDateLimitSchedules = dueDateLimitSchedules;

    period = Period.from(duration, intervalId);

    loanPolicyRelatedError = this::validationError;
  }

  @Override
  HttpResult<DateTime> calculateInitialDueDate(Loan loan) {
    final DateTime loanDate = loan.getLoanDate();

    logApplying(String.format("Rolling %s %s due date calculation", duration, intervalId));

    return period.addTo(loanDate,
      () -> loanPolicyRelatedError.apply(UNRECOGNISED_PERIOD_MESSAGE),
      interval -> loanPolicyRelatedError.apply(String.format(UNRECOGNISED_INTERVAL_MESSAGE, interval)),
      duration -> loanPolicyRelatedError.apply(String.format(INVALID_DURATION_MESSAGE, duration)))
      .next(dueDate -> limitDueDateBySchedule(loanDate, dueDate));
  }

  @Override
  HttpResult<DateTime> calculateRenewalDueDate(Loan loan, DateTime systemDate) {
    return period.addTo(systemDate,
      () -> loanPolicyRelatedError.apply(UNRECOGNISED_PERIOD_MESSAGE),
      interval -> loanPolicyRelatedError.apply(String.format(UNRECOGNISED_INTERVAL_MESSAGE, interval)),
      duration -> loanPolicyRelatedError.apply(String.format(INVALID_DURATION_MESSAGE, duration)));
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
