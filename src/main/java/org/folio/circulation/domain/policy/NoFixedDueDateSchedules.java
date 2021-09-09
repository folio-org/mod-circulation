package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.results.Result.succeeded;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Supplier;

import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

public class NoFixedDueDateSchedules extends FixedDueDateSchedules {
  public NoFixedDueDateSchedules() {
    super(null, new ArrayList<>());
  }

  @Override
  public Optional<ZonedDateTime> findDueDateFor(ZonedDateTime date) {
    return Optional.empty();
  }

  @Override
  Result<ZonedDateTime> truncateDueDate(
    ZonedDateTime dueDate,
    ZonedDateTime loanDate,
    Supplier<ValidationError> noApplicableScheduleError) {

    return succeeded(dueDate);
  }
}
