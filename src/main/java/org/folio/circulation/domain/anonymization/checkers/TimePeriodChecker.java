package org.folio.circulation.domain.anonymization.checkers;

import org.folio.circulation.domain.policy.Period;
import org.joda.time.DateTime;

abstract class TimePeriodChecker extends DefaultAnonymizationChecker {

  private Period period;

  TimePeriodChecker(Period period) {
    this.period = period;
  }

  boolean checkTimePeriodPassed(DateTime startDate) {
    return startDate != null && DateTime.now().isAfter(startDate.plus(period.timePeriod()));
  }

}