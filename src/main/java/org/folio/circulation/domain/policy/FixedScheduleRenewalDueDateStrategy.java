package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.ErrorCode.RENEWAL_DATE_OUTSIDE_FIXED_LOAN_POLICY_DATE_RANGES;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.ErrorCode;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

class FixedScheduleRenewalDueDateStrategy extends DueDateStrategy {
  private static final String NO_APPLICABLE_DUE_DATE_SCHEDULE_MESSAGE =
    "renewal date falls outside of date ranges in fixed loan policy";
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final FixedDueDateSchedules fixedDueDateSchedules;
  private final ZonedDateTime systemDate;

  FixedScheduleRenewalDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    FixedDueDateSchedules fixedDueDateSchedules,
    ZonedDateTime systemDate,
    Function<String, ValidationError> errorForPolicy) {

    this(loanPolicyId, loanPolicyName, fixedDueDateSchedules, systemDate,
      (message, errorCode) -> errorForPolicy.apply(message));
  }

  FixedScheduleRenewalDueDateStrategy(
    String loanPolicyId,
    String loanPolicyName,
    FixedDueDateSchedules fixedDueDateSchedules,
    ZonedDateTime systemDate,
    BiFunction<String, ErrorCode, ValidationError> errorForPolicy) {

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
    log.debug("calculateDueDate:: parameters loan: {}", loan);

    try {
      return fixedDueDateSchedules.findDueDateFor(systemDate)
        .map(Result::succeeded)
        .orElseGet(() -> failedValidation(
          errorForPolicy(NO_APPLICABLE_DUE_DATE_SCHEDULE_MESSAGE,
            RENEWAL_DATE_OUTSIDE_FIXED_LOAN_POLICY_DATE_RANGES)));
    } catch (Exception e) {
      log.error("calculateDueDate:: Error occurred during fixed schedule "
        + "renewal due date calculation: {}", e.getMessage());
      return failedDueToServerError(e);
    }
  }
}
