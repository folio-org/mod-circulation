package org.folio.circulation.domain.notice.combiner;

import java.util.Collection;

import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;

import io.vertx.core.json.JsonObject;

public interface NoticeContextCombiner {
  JsonObject buildCombinedNoticeContext(Collection<PatronNoticeEvent> events);
  NoticeLogContext buildCombinedNoticeLogContext(Collection<PatronNoticeEvent> events);
}
