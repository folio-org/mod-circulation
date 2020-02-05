package org.folio.circulation.domain.policy;

import static java.lang.String.format;

import java.util.function.Function;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;

class RollingCheckOutDueDateStrategy extends DueDateStrategy {
  private static final String NO_APPLICABLE_DUE_DATE_LIMIT_SCHEDULE_MESSAGE =
    "loan date falls outside of the date ranges in the loan policy";

  private static final String CHECK_OUT_UNRECOGNISED_INTERVAL_MESSAGE =
    "the interval \"%s\" in the loan policy is not recognised";

  private static final String CHECKOUT_INVALID_DURATION_MESSAGE =
    "the duration \"%s\" in the loan policy is invalid";

  private static final String CHECK_OUT_UNRECOGNISED_PERIOD_MESSAGE =
    "the loan period in the loan policy is not recognised";

  private final Period period;
  private final FixedDueDateSchedules dueDateLimitSchedules;

  RollingCheckOutDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    Period period,
    FixedDueDateSchedules dueDateLimitSchedules,
    Function<String, ValidationError> errorForPolicy) {

    super(loanPolicyId, loanPolicyName, errorForPolicy);
    this.period = period;
    this.dueDateLimitSchedules = dueDateLimitSchedules;
  }

  @Override
  public Result<DateTime> calculateDueDate(Loan loan) {
    final DateTime loanDate = loan.getLoanDate();

    return initialDueDate(loanDate)
      .next(dueDate -> truncateDueDateBySchedule(loanDate, dueDate));
  }

  private Result<DateTime> initialDueDate(DateTime loanDate) {
    return period.addTo(loanDate,
      () -> errorForPolicy(CHECK_OUT_UNRECOGNISED_PERIOD_MESSAGE),
      interval -> errorForPolicy(format(CHECK_OUT_UNRECOGNISED_INTERVAL_MESSAGE, interval)),
      duration -> errorForPolicy(format(CHECKOUT_INVALID_DURATION_MESSAGE, duration)));
  }

  private Result<DateTime> truncateDueDateBySchedule(
    DateTime loanDate,
    DateTime dueDate) {

    return dueDateLimitSchedules.truncateDueDate(dueDate, loanDate,
      () -> errorForPolicy(NO_APPLICABLE_DUE_DATE_LIMIT_SCHEDULE_MESSAGE));
  }
}
