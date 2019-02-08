package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.joda.time.DateTime;

public class KeepCurrentStrategy extends ClosedLibraryStrategy {

  public KeepCurrentStrategy(LoanPolicyPeriod loanPeriod) {
    super(loanPeriod);
  }

  @Override
  protected DateTime calculateIfClosed(DateTime requestedDate, AdjustingOpeningDays adjustingOpeningDays) {
    return requestedDate;
  }
}
