package org.folio.circulation.domain.policy;

import java.util.Optional;
import java.util.function.Function;

import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;

public class DefaultFixedScheduleCheckOutDueDateStrategy extends AbstractFixedScheduleCheckOutDueDateStrategy {

  public DefaultFixedScheduleCheckOutDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    FixedDueDateSchedules fixedDueDateSchedules, 
    Function<String, ValidationError> errorForPolicy) {

    super(loanPolicyId, loanPolicyName, fixedDueDateSchedules, errorForPolicy);
  }

  @Override
  Optional<DateTime> getDueDate(DateTime loanDate) {
    return fixedDueDateSchedules.findDueDateFor(loanDate);
  }

}