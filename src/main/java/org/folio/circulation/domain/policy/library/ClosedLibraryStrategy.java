package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;

public interface ClosedLibraryStrategy {

  HttpResult<DateTime> calculateDueDate(DateTime requestedDate, AdjustingOpeningDays openingDays);
}
