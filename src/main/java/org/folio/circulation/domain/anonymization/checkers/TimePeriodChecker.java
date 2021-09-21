package org.folio.circulation.domain.anonymization.checkers;

import static org.folio.circulation.support.utils.ClockUtil.getDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.isAfterMillis;

import org.folio.circulation.domain.policy.Period;
import org.joda.time.DateTime;

abstract class TimePeriodChecker implements AnonymizationChecker {

  private Period period;

  TimePeriodChecker(Period period) {
    this.period = period;
  }

  boolean checkTimePeriodPassed(DateTime startDate) {
    return startDate != null && isAfterMillis(getDateTime(), startDate.plus(period.timePeriod()));
  }

}
