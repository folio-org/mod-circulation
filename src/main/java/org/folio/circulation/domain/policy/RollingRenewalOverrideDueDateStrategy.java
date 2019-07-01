package org.folio.circulation.domain.policy;

import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;

import java.util.function.Function;

/**
 * Overrides {@link #calculateDueDate(DateTime)} in {@link RollingRenewalDueDateStrategy}
 * to skip due date truncating
 */
class RollingRenewalOverrideDueDateStrategy extends RollingRenewalDueDateStrategy {

  RollingRenewalOverrideDueDateStrategy(String loanPolicyId, String loanPolicyName, DateTime systemDate,
                                        String renewFrom, Period period,
                                        FixedDueDateSchedules dueDateLimitSchedules,
                                        Function<String, ValidationError> errorForPolicy) {
    super(loanPolicyId, loanPolicyName, systemDate, renewFrom, period, dueDateLimitSchedules, errorForPolicy);
  }

  @Override
  protected Result<DateTime> calculateDueDate(DateTime from) {
    return super.renewalDueDate(from);
  }
}

