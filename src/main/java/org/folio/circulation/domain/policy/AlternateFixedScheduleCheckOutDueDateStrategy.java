package org.folio.circulation.domain.policy;

import java.util.Optional;
import java.util.function.Function;

import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;

public class AlternateFixedScheduleCheckOutDueDateStrategy extends AbstractFixedScheduleCheckOutDueDateStrategy {

  public AlternateFixedScheduleCheckOutDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    FixedDueDateSchedules fixedDueDateSchedules, 
    Function<String, ValidationError> errorForPolicy) {

    super(loanPolicyId, loanPolicyName, fixedDueDateSchedules, errorForPolicy);
  }

  @Override
  Optional<DateTime> getDueDate(DateTime loanDate) {
    return fixedDueDateSchedules.findEarliestDueDateFor(loanDate);
  }

}