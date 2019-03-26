package org.folio.circulation.domain.policy;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Supplier;

import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;
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
  Result<DateTime> truncateDueDate(
    DateTime dueDate,
    DateTime loanDate,
    Supplier<ValidationError> noApplicableScheduleError) {

    return Result.succeeded(dueDate);
  }
}
