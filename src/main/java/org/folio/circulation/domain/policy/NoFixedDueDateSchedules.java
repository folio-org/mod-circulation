package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.results.Result.succeeded;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Supplier;

import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;

public class NoFixedDueDateSchedules extends FixedDueDateSchedules {
  public NoFixedDueDateSchedules() {
    super(null, new ArrayList<>());
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

    return succeeded(dueDate);
  }
}
