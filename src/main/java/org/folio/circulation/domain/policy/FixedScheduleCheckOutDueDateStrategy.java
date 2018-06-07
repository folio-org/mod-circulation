package org.folio.circulation.domain.policy;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;

import java.util.function.Function;

import static org.folio.circulation.support.HttpResult.failure;

class FixedScheduleCheckOutDueDateStrategy extends DueDateStrategy {
  private static final String NO_APPLICABLE_DUE_DATE_SCHEDULE_MESSAGE =
    "loan date falls outside of the date ranges in the loan policy";

  private final FixedDueDateSchedules fixedDueDateSchedules;

  FixedScheduleCheckOutDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    FixedDueDateSchedules fixedDueDateSchedules, Function<String,
    ValidationError> errorForPolicy) {

    super(loanPolicyId, loanPolicyName, errorForPolicy);

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

    logApplying("Fixed schedule check out due date calculation");

    try {
      return fixedDueDateSchedules.findDueDateFor(loanDate)
        .map(HttpResult::success)
        .orElseGet(() -> failure(
          validationError(NO_APPLICABLE_DUE_DATE_SCHEDULE_MESSAGE)));
    }
    catch(Exception e) {
      logException(e, "Error occurred during fixed schedule check out due date calculation");
      return failure(new ServerErrorFailure(e));
    }
  }
}
