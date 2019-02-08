package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.joda.time.DateTime;

public class EndOfNextOpenDayStrategy extends ClosedLibraryStrategy {

  public EndOfNextOpenDayStrategy(LoanPolicyPeriod loanPeriod) {
    super(loanPeriod);
  }

  @Override
  protected DateTime calculateIfClosed(DateTime requestedDate, AdjustingOpeningDays adjustingOpeningDays) {
    OpeningDay nextDay = adjustingOpeningDays.getNextDay();
    DateTime nextDateTime = getTermDueDate(nextDay);
    return calculateNewInitialDueDate(nextDateTime);
  }
}
