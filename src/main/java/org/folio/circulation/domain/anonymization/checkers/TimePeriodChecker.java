package org.folio.circulation.domain.anonymization.checkers;

import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.ClockManager;

import org.joda.time.DateTime;

abstract class TimePeriodChecker implements AnonymizationChecker {

  private Period period;

  TimePeriodChecker(Period period) {
    this.period = period;
  }

  boolean checkTimePeriodPassed(DateTime startDate) {
    return startDate != null && ClockManager.getClockManager().getDateTime()
      .isAfter(startDate.plus(period.timePeriod()));
  }

}
