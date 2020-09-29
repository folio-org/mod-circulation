package org.folio.circulation.domain.notice;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;

@AllArgsConstructor
@Getter
public class NoticeEventBundle {
  private final PatronNoticeEvent event;
  private final NoticeLogContext logContext;
}
