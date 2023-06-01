package org.folio.circulation.domain.notice.schedule.grouping;

import org.folio.circulation.domain.notice.schedule.ScheduledNotice;

public class DefaultScheduledNoticeGroupDefinitionFactory
  implements ScheduledNoticeGroupDefinitionFactory {

  @Override
  public ScheduledNoticeGroupDefinition newInstance(ScheduledNotice notice) {
    return new ScheduledNoticeGroupDefinition(notice);
  }
}
