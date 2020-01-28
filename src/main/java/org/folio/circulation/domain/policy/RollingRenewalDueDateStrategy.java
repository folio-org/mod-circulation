package org.folio.circulation.domain.policy;

import static java.lang.String.format;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;

class RollingRenewalDueDateStrategy extends DueDateStrategy {
  private static final String RENEW_FROM_SYSTEM_DATE = "SYSTEM_DATE";
  private static final String RENEW_FROM_DUE_DATE = "CURRENT_DUE_DATE";

  private static final String NO_APPLICABLE_DUE_DATE_LIMIT_SCHEDULE_MESSAGE =
    "renewal date falls outside of date ranges in the loan policy";

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

  RollingRenewalDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    DateTime systemDate,
    String renewFrom,
    Period period,
    FixedDueDateSchedules dueDateLimitSchedules,
    Function<String, ValidationError> errorForPolicy) {

    super(loanPolicyId, loanPolicyName, errorForPolicy);
    this.systemDate = systemDate;
    this.renewFrom = renewFrom;
    this.period = period;
    this.dueDateLimitSchedules = dueDateLimitSchedules;
  }

  @Override
  public Result<DateTime> calculateDueDate(Loan loan) {
    if(StringUtils.isBlank(renewFrom)) {
      return failedValidation(errorForPolicy(RENEW_FROM_UNRECOGNISED_MESSAGE));
    }

    switch (renewFrom) {
      case RENEW_FROM_DUE_DATE:
        return calculateDueDate(loan.getDueDate());
      case RENEW_FROM_SYSTEM_DATE:
        return calculateDueDate(systemDate);
      default:
        return failedValidation(errorForPolicy(RENEW_FROM_UNRECOGNISED_MESSAGE));
    }
  }

  protected Result<DateTime> calculateDueDate(DateTime from) {
    return renewalDueDate(from)
      .next(dueDate -> truncateDueDateBySchedule(from, dueDate));
  }

  Result<DateTime> renewalDueDate(DateTime from) {
    return period.addTo(from,
      () -> errorForPolicy(RENEWAL_UNRECOGNISED_PERIOD_MESSAGE),
      interval -> errorForPolicy(format(RENEWAL_UNRECOGNISED_INTERVAL_MESSAGE, interval)),
      duration -> errorForPolicy(format(RENEWAL_INVALID_DURATION_MESSAGE, duration)));
  }

  private Result<DateTime> truncateDueDateBySchedule(
    DateTime from,
    DateTime dueDate) {

    return dueDateLimitSchedules.truncateDueDate(dueDate, from,
      () -> errorForPolicy(NO_APPLICABLE_DUE_DATE_LIMIT_SCHEDULE_MESSAGE));
  }
}
