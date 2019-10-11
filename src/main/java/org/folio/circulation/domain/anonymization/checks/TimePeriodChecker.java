package org.folio.circulation.domain.anonymization.checks;

import org.folio.circulation.domain.policy.Period;
import org.joda.time.DateTime;

abstract class TimePeriodChecker implements AnonymizationChecker {

  private Period period;

  TimePeriodChecker(Period period) {
    this.period = period;
  }

  boolean checkTimePeriodPassed(DateTime startDate) {
    return DateTime.now().isAfter(startDate.plus(period.timePeriod()));
  }

}