package org.folio.circulation.domain.notice.combiner;

import java.util.Collection;

import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;

import io.vertx.core.json.JsonObject;

public class SingleNoticeContextCombiner implements NoticeContextCombiner {

  @Override
  public JsonObject buildCombinedNoticeContext(Collection<PatronNoticeEvent> events) {
    return events.stream()
      .map(PatronNoticeEvent::getNoticeContext)
      .findFirst()
      .orElseGet(JsonObject::new);
  }

  @Override
  public NoticeLogContext buildCombinedNoticeLogContext(Collection<PatronNoticeEvent> events) {
    return events.stream()
      .map(PatronNoticeEvent::getNoticeLogContext)
      .findFirst()
      .orElseGet(NoticeLogContext::new);
  }
}
