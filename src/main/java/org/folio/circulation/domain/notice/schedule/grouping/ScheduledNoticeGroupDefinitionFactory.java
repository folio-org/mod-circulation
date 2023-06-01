package org.folio.circulation.domain.notice.schedule.grouping;

import org.folio.circulation.domain.notice.schedule.ScheduledNotice;

public interface ScheduledNoticeGroupDefinitionFactory {
  ScheduledNoticeGroupDefinition newInstance(ScheduledNotice scheduledNotice);
}
