package org.folio.circulation.domain.notice;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.notice.schedule.ScheduledNoticeConfig;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class ScheduledPatronNoticeService extends PatronNoticeService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());


  public ScheduledPatronNoticeService(Clients clients) {
    super(clients);
  }

  public CompletableFuture<Result<Void>> sendNotice(ScheduledNoticeConfig noticeConfig,
    String recipientId, JsonObject context, NoticeLogContext noticeLogContext) {

    return sendNotice(new PatronNotice(recipientId, context, noticeConfig), noticeLogContext);
  }
}
