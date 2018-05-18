package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
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
    this.fixedDueDateSchedules = fixedDueDateSchedules;
  }

  @Override
  HttpResult<DateTime> calculate(JsonObject loan) {
    final DateTime loanDate = DateTime.parse(loan.getString("loanDate"));

    logApplying("Fixed schedule due date calculation");

    if(fixedDueDateSchedules == null) {
      return fail(NO_APPLICABLE_DUE_DATE_SCHEDULE_MESSAGE);
    }

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
