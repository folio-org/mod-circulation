package org.folio.circulation.domain.policy;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;

import java.util.function.Function;

import static org.folio.circulation.support.HttpResult.failed;

class FixedScheduleRenewalDueDateStrategy extends DueDateStrategy {
  private static final String NO_APPLICABLE_DUE_DATE_SCHEDULE_MESSAGE =
    "renewal date falls outside of the date ranges in the loan policy";

  private final FixedDueDateSchedules fixedDueDateSchedules;
  private final DateTime systemDate;

  FixedScheduleRenewalDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    FixedDueDateSchedules fixedDueDateSchedules,
    DateTime systemDate,
    Function<String, ValidationError> errorForPolicy) {

    super(loanPolicyId, loanPolicyName, errorForPolicy);

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
        .map(HttpResult::succeeded)
        .orElseGet(() -> failed(
          validationError(NO_APPLICABLE_DUE_DATE_SCHEDULE_MESSAGE)));
    }
    catch(Exception e) {
      logException(e, "Error occurred during fixed schedule renewal due date calculation");
      return failed(new ServerErrorFailure(e));
    }
  }
}
