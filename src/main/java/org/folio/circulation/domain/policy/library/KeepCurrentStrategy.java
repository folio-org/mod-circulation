package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.joda.time.DateTime;

public class KeepCurrentStrategy implements ClosedLibraryStrategy {

  @Override
  public DateTime calculateDueDate(DateTime requestedDate, AdjustingOpeningDays adjustingOpeningDays) {
    return requestedDate;
  }
}
