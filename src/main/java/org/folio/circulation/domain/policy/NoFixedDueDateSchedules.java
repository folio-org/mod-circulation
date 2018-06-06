package org.folio.circulation.domain.policy;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Supplier;

class NoFixedDueDateSchedules extends FixedDueDateSchedules {
  NoFixedDueDateSchedules() {
    super(new ArrayList<>());
  }

  @Override
  Optional<DateTime> findDueDateFor(DateTime date) {
    return Optional.empty();
  }

  @Override
  HttpResult<DateTime> truncateDueDate(
    DateTime dueDate,
    DateTime loanDate,
    Supplier<ValidationErrorFailure> noApplicableScheduleError) {

    return HttpResult.success(dueDate);
  }
}
