package org.folio.circulation.domain.notice.schedule.grouping;

import static org.folio.circulation.domain.notice.NoticeTiming.UPON_AT;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.OVERDUE_FINE_RETURNED;

import org.folio.circulation.domain.notice.schedule.ScheduledNotice;

public class OverdueFineScheduledNoticeGroupDefinitionFactory
  implements ScheduledNoticeGroupDefinitionFactory {

  @Override
  public ScheduledNoticeGroupDefinition newInstance(ScheduledNotice notice) {
    if (notice.getTriggeringEvent() == OVERDUE_FINE_RETURNED
      && notice.getConfiguration().getTiming() == UPON_AT) {

      return new SessionAwareScheduledNoticeGroupDefinition(notice);
    }

    return new ScheduledNoticeGroupDefinition(notice);
  }
}
