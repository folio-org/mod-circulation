package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.support.results.Result.succeeded;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;

public class KeepCurrentDateTimeStrategy implements ClosedLibraryStrategy {

  @Override
  public Result<DateTime> calculateDueDate(DateTime requestedDate, AdjacentOpeningDays openingDays) {
    return succeeded(requestedDate);
  }
}
