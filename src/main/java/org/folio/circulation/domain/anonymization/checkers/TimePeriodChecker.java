package org.folio.circulation.domain.anonymization.checkers;

import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.isAfterMillis;

import java.time.ZonedDateTime;

import org.folio.circulation.domain.policy.Period;

abstract class TimePeriodChecker implements AnonymizationChecker {

  private Period period;

  TimePeriodChecker(Period period) {
    this.period = period;
  }

  boolean checkTimePeriodPassed(ZonedDateTime startDate) {
    return startDate != null && isAfterMillis(getZonedDateTime(), period.plusDate(startDate));
  }

}
