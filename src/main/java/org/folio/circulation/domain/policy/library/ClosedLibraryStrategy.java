package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.joda.time.DateTime;

public interface ClosedLibraryStrategy {

  DateTime calculateDueDate(DateTime requestedDate, AdjustingOpeningDays openingDays);
}
