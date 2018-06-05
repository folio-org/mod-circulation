package org.folio.circulation.domain.policy;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.joda.time.DateTime;

class FixedScheduleDueDateStrategy extends DueDateStrategy {
  private static final String NO_APPLICABLE_DUE_DATE_SCHEDULE_MESSAGE =
    "Item can't be checked out as the loan date falls outside of the date ranges in the loan policy.";

  private final FixedDueDateSchedules fixedDueDateSchedules;

  FixedScheduleDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    FixedDueDateSchedules fixedDueDateSchedules) {

    super(loanPolicyId, loanPolicyName);

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
    final DateTime loanDate = loan.getLoanDate();

    logApplying("Fixed schedule due date calculation");

    try {
      return fixedDueDateSchedules.findDueDateFor(loanDate)
        .map(HttpResult::success)
        .orElseGet(() -> fail(NO_APPLICABLE_DUE_DATE_SCHEDULE_MESSAGE));
    }
    catch(Exception e) {
      logException(e, "Error occurred during fixed schedule due date calculation");
      return HttpResult.failure(new ServerErrorFailure(e));
    }
  }
}
