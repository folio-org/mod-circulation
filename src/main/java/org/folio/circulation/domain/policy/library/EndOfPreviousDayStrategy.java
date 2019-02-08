package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.joda.time.DateTime;

public class EndOfPreviousDayStrategy extends ClosedLibraryStrategy {

  public EndOfPreviousDayStrategy(LoanPolicyPeriod loanPeriod) {
    super(loanPeriod);
  }

  @Override
  protected DateTime calculateIfClosed(DateTime requestedDate, AdjustingOpeningDays adjustingOpeningDays) {
    OpeningDay prevDayPeriod = adjustingOpeningDays.getPreviousDay();
    DateTime prevDateTime = getTermDueDate(prevDayPeriod);
    return calculateNewInitialDueDate(prevDateTime);
  }
}
