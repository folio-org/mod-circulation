package org.folio.circulation.domain.policy;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

/**
 * Overrides {@link #calculateDueDate(ZonedDateTime)} in {@link RollingRenewalDueDateStrategy}
 * to skip due date truncating
 */
class RollingRenewalOverrideDueDateStrategy extends RollingRenewalDueDateStrategy {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  RollingRenewalOverrideDueDateStrategy(String loanPolicyId, String loanPolicyName, ZonedDateTime systemDate,
                                        String renewFrom, Period period,
                                        FixedDueDateSchedules dueDateLimitSchedules,
                                        Function<String, ValidationError> errorForPolicy) {
    super(loanPolicyId, loanPolicyName, systemDate, renewFrom, period, dueDateLimitSchedules, errorForPolicy);
  }

  @Override
  protected Result<ZonedDateTime> calculateDueDate(ZonedDateTime from) {
    log.debug("calculateDueDate parameters from: {}", from);
    return super.renewalDueDate(from);
  }
}

