package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.support.results.Result.succeeded;

import java.time.ZonedDateTime;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.support.results.Result;

public class KeepCurrentDateTimeStrategy implements ClosedLibraryStrategy {

  @Override
  public Result<ZonedDateTime> calculateDueDate(ZonedDateTime requestedDate, AdjacentOpeningDays openingDays) {
    return succeeded(requestedDate);
  }
}
