package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.time.ZonedDateTime;
import java.util.function.Function;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.http.server.error.ValidationError;
import org.folio.circulation.support.results.Result;

class FixedScheduleRenewalDueDateStrategy extends DueDateStrategy {
  private static final String NO_APPLICABLE_DUE_DATE_SCHEDULE_MESSAGE =
    "renewal date falls outside of date ranges in fixed loan policy";

  private final FixedDueDateSchedules fixedDueDateSchedules;
  private final ZonedDateTime systemDate;

  FixedScheduleRenewalDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    FixedDueDateSchedules fixedDueDateSchedules,
    ZonedDateTime systemDate,
    Function<String, ValidationError> errorForPolicy) {

    super(loanPolicyId, loanPolicyName, errorForPolicy);

    this.systemDate = systemDate;

    //TODO: Find a better way to fail
    if (fixedDueDateSchedules != null) {
      this.fixedDueDateSchedules = fixedDueDateSchedules;
    } else {
      this.fixedDueDateSchedules = new NoFixedDueDateSchedules();
    }
  }

  @Override
  public Result<ZonedDateTime> calculateDueDate(Loan loan) {
    logApplying("Fixed schedule renewal due date calculation");

    try {
      return fixedDueDateSchedules.findDueDateFor(systemDate)
        .map(Result::succeeded)
        .orElseGet(() -> failedValidation(
          errorForPolicy(NO_APPLICABLE_DUE_DATE_SCHEDULE_MESSAGE)));
    } catch (Exception e) {
      logException(e, "Error occurred during fixed schedule renewal due date calculation");
      return failedDueToServerError(e);
    }
  }
}
