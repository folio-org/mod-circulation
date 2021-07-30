package org.folio.circulation.domain.notice;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.notice.schedule.ScheduledNoticeConfig;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class ScheduledPatronNoticeService extends PatronNoticeService {

  public ScheduledPatronNoticeService(Clients clients) {
    super(clients);
  }

  public CompletableFuture<Result<Void>> sendNotice(ScheduledNoticeConfig noticeConfig,
    String recipientId, JsonObject context, NoticeLogContext noticeLogContext) {

    return sendNotice(new PatronNotice(recipientId, context, noticeConfig), noticeLogContext);
  }
}
