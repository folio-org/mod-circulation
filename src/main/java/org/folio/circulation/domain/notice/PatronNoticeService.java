package org.folio.circulation.domain.notice;

import static org.apache.http.HttpStatus.SC_OK;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.logging.PatronNoticeLogHelper;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public abstract class PatronNoticeService {

  private final CollectionResourceClient patronNoticeClient;
  private final EventPublisher eventPublisher;

  protected PatronNoticeService(Clients clients) {
    this.patronNoticeClient = clients.patronNoticeClient();
    this.eventPublisher = new EventPublisher(clients.pubSubPublishingService());
  }

  protected CompletableFuture<Result<Void>> sendNotice(PatronNotice patronNotice,
    NoticeLogContext noticeLogContext) {

    return patronNoticeClient.post(JsonObject.mapFrom(patronNotice))
      .thenApply(r ->  new ResponseInterpreter<Response>().on(200, r).flatMap(r))
      .whenComplete((r, t) -> logResult(patronNotice, noticeLogContext, r, t))
      .thenApply(r -> r.map(ignored -> null));
  }

  private CompletableFuture<Result<Void>> logResult(PatronNotice patronNotice,
    NoticeLogContext noticeLogContext, Result<Response> result, Throwable throwable) {

    PatronNoticeLogHelper.logResponse(result, throwable, SC_OK, patronNotice);

    return eventPublisher.publishNoticeLogEvent(noticeLogContext, result, throwable);
  }

}
