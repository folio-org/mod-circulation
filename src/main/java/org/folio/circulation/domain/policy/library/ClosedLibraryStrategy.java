package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;

public interface ClosedLibraryStrategy {

  Result<DateTime> calculateDueDate(DateTime requestedDate, AdjacentOpeningDays openingDays);
}
