package org.folio.circulation.domain.policy.library;

import java.time.ZonedDateTime;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.support.results.Result;

public interface ClosedLibraryStrategy {

  Result<ZonedDateTime> calculateDueDate(ZonedDateTime requestedDate, AdjacentOpeningDays openingDays);
}
