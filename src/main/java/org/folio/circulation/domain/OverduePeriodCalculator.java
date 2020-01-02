package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.support.Clients;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;

public class OverduePeriodCalculator {
  private static final int MINUTES_IN_HOUR = 60;
  private static final int MINUTES_IN_DAY = 1140;
  private static final int MINUTES_IN_WEEK = 10080;
  private static final int MINUTES_IN_MONTH = 44640;

  private Loan loan;
  private LoanPolicyRepository loanPolicyRepository;

  public OverduePeriodCalculator(Clients clients, Loan loan) {
    this.loan = loan;
    this.loanPolicyRepository = new LoanPolicyRepository(clients);
  }

  public int getOverdueMinutes(Loan loan) {
    LoanPolicy loanPolicy = loan.getLoanPolicy();
    OverdueFinePolicy overdueFinePolicy = loan.getOverdueFinePolicy();

    int gracePeriodMinutes = getGracePeriodMinutes(loanPolicy, overdueFinePolicy);
    int overdueMinutes = getOverdueMinutes(loan, overdueFinePolicy);

    if (overdueMinutes > gracePeriodMinutes) {
      return overdueMinutes - gracePeriodMinutes;
    } else {
      return 0;
    }

  }

  private int getGracePeriodMinutes(LoanPolicy loanPolicy, OverdueFinePolicy overdueFinePolicy) {
    if (loan.wasDueDateChangedByRecall() && overdueFinePolicy.shouldIgnoreGracePeriodsForRecalls()) {
      return 0;
    }
    else {
      int duration = loanPolicy.getGracePeriodDuration();
      if (duration > 0) {
        switch (loanPolicy.getGracePeriodInterval()) {
        case MINUTES:
          return duration;
        case HOURS:
          return duration * MINUTES_IN_HOUR;
        case DAYS:
          return duration * MINUTES_IN_DAY;
        case WEEKS:
          return duration * MINUTES_IN_WEEK;
        case MONTHS:
          return duration * MINUTES_IN_MONTH;
        default:
          return duration;
        }
      }
      else return 0;
    }
  }

  private int getOverdueMinutes(Loan loan, OverdueFinePolicy overdueFinePolicy) {
    if (overdueFinePolicy.shouldCountClosed()) {
      DateTime systemTime = DateTime.now(DateTimeZone.UTC);
      if (loan.getDueDate().isBefore(systemTime)) {
        return new Period(loan.getDueDate(), systemTime).getMinutes();
      } else {
        return 0;
      }
    } else {
      // TODO: Access Library Calendar to set overdueMinutes to total open minutes between Due date (from Loan record)
      // and current System Date/Time <=====means the institution only wants to count the time they have been open
      return 0;
    }
  }

}
