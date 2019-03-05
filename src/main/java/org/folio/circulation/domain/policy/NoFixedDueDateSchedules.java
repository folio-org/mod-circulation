package org.folio.circulation.domain.policy;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Supplier;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;

class NoFixedDueDateSchedules extends FixedDueDateSchedules {
  NoFixedDueDateSchedules() {
    super(new ArrayList<>());
  }

  @Override
  public Optional<DateTime> findDueDateFor(DateTime date) {
    return Optional.empty();
  }

  @Override
  HttpResult<DateTime> truncateDueDate(
    DateTime dueDate,
    DateTime loanDate,
    Supplier<ValidationErrorFailure> noApplicableScheduleError) {

    return HttpResult.succeeded(dueDate);
  }
}
