package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;

public interface ClosedLibraryStrategy {

  HttpResult<DateTime> calculateDueDate(DateTime requestedDate, AdjacentOpeningDays openingDays);
}
