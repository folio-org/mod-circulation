package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.support.PeriodUtil;
import org.joda.time.DateTime;

import java.util.function.BiPredicate;

public class LibraryIsOpenPredicate implements BiPredicate<DateTime, AdjustingOpeningDays> {

  public static LibraryIsOpenPredicate fromLoanPeriod(LoanPolicyPeriod loanPolicyPeriod) {
    return new LibraryIsOpenPredicate(LoanPolicyPeriod.isShortTermLoans(loanPolicyPeriod));
  }

  private final boolean isShortTermLoan;

  public LibraryIsOpenPredicate(boolean isShortTerm) {
    this.isShortTermLoan = isShortTerm;
  }

  @Override
  public boolean test(DateTime requestedDate, AdjustingOpeningDays adjustingOpeningDays) {
    if (adjustingOpeningDays == null) {
      return false;
    }
    OpeningDay requestedDay = adjustingOpeningDays.getRequestedDay();
    if (!requestedDay.getOpen()) {
      return false;
    }
    if (isShortTermLoan) {
      return PeriodUtil.isDateTimeWithDurationInsideDay(
        requestedDay, requestedDate.toLocalTime());
    }
    return true;
  }
}
