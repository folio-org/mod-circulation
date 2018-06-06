package org.folio.circulation.domain.policy;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.joda.time.DateTime;

import static org.folio.circulation.support.HttpResult.failure;

class FixedScheduleRenewalDueDateStrategy extends DueDateStrategy {
  private static final String NO_APPLICABLE_DUE_DATE_SCHEDULE_MESSAGE =
    "renewal date falls outside of the date ranges in the loan policy";

  private final FixedDueDateSchedules fixedDueDateSchedules;
  private final DateTime systemDate;

  FixedScheduleRenewalDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    FixedDueDateSchedules fixedDueDateSchedules,
    DateTime systemDate) {

    super(loanPolicyId, loanPolicyName);

    this.systemDate = systemDate;

    //TODO: Find a better way to fail
    if(fixedDueDateSchedules != null) {
      this.fixedDueDateSchedules = fixedDueDateSchedules;
    }
    else {
      this.fixedDueDateSchedules = new NoFixedDueDateSchedules();
    }
  }

  @Override
  HttpResult<DateTime> calculateDueDate(Loan loan) {
    logApplying("Fixed schedule renewal due date calculation");

    try {
      return fixedDueDateSchedules.findDueDateFor(systemDate)
        .map(HttpResult::success)
        .orElseGet(() -> fail(NO_APPLICABLE_DUE_DATE_SCHEDULE_MESSAGE));
    }
    catch(Exception e) {
      logException(e, "Error occurred during fixed schedule renewal due date calculation");
      return failure(new ServerErrorFailure(e));
    }
  }
}
