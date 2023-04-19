package org.folio.circulation.domain.policy;

import static java.lang.String.format;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.utils.LogUtil.asJson;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

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
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final ZonedDateTime systemDate;
  private final String renewFrom;
  private final Period period;
  private final FixedDueDateSchedules dueDateLimitSchedules;

  RollingRenewalDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    ZonedDateTime systemDate,
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
  public Result<ZonedDateTime> calculateDueDate(Loan loan) {
    log.debug("calculateDueDate:: parameters loan: {}", () -> loan);
    if(StringUtils.isBlank(renewFrom)) {
      log.error("calculateDueDate:: renewFrom is blank");
      return failedValidation(errorForPolicy(RENEW_FROM_UNRECOGNISED_MESSAGE));
    }
    log.info("calculateDueDate:: renewFrom: {}", renewFrom);
    switch (renewFrom) {
      case RENEW_FROM_DUE_DATE:
        return calculateDueDate(loan.getDueDate());
      case RENEW_FROM_SYSTEM_DATE:
        return calculateDueDate(systemDate);
      default:
        return failedValidation(errorForPolicy(RENEW_FROM_UNRECOGNISED_MESSAGE));
    }
  }

  protected Result<ZonedDateTime> calculateDueDate(ZonedDateTime from) {
    return renewalDueDate(from)
      .next(dueDate -> truncateDueDateBySchedule(from, dueDate));
  }

  Result<ZonedDateTime> renewalDueDate(ZonedDateTime from) {
    return period.addTo(from,
      () -> errorForPolicy(RENEWAL_UNRECOGNISED_PERIOD_MESSAGE),
      interval -> errorForPolicy(format(RENEWAL_UNRECOGNISED_INTERVAL_MESSAGE, interval)),
      duration -> errorForPolicy(format(RENEWAL_INVALID_DURATION_MESSAGE, duration)));
  }

  private Result<ZonedDateTime> truncateDueDateBySchedule(
    ZonedDateTime from,
    ZonedDateTime dueDate) {

    return dueDateLimitSchedules.truncateDueDate(dueDate, from,
      () -> errorForPolicy(NO_APPLICABLE_DUE_DATE_LIMIT_SCHEDULE_MESSAGE));
  }
}
