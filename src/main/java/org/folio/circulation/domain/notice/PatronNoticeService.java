package org.folio.circulation.domain.notice;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.folio.HttpStatus.HTTP_OK;

import io.vertx.core.json.JsonObject;
import java.util.concurrent.CompletableFuture;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.logging.PatronNoticeLogHelper;
import org.folio.circulation.support.results.Result;

public abstract class PatronNoticeService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient patronNoticeClient;
  private final EventPublisher eventPublisher;

  protected PatronNoticeService(Clients clients) {
    this.patronNoticeClient = clients.patronNoticeClient();
    this.eventPublisher = new EventPublisher(clients);
  }

  public CompletableFuture<Result<Void>> sendNotice(PatronNotice patronNotice,
    NoticeLogContext noticeLogContext) {

    log.info("sendNotice:: sending notice to recipient {}, template {}",
      patronNotice != null ? patronNotice.getRecipientId() : "null",
      patronNotice != null && patronNotice.getTemplateId() != null ? patronNotice.getTemplateId() : "null");

    log.debug("sendNotice:: posting notice to patron notice client");
    return patronNoticeClient.post(JsonObject.mapFrom(patronNotice))
      .thenApply(r ->  new ResponseInterpreter<Response>().on(200, r).flatMap(r))
      .whenComplete((r, t) -> logResult(patronNotice, noticeLogContext, r, t))
      .thenApply(r -> r.map(ignored -> null));
  }

  private CompletableFuture<Result<Void>> logResult(PatronNotice patronNotice,
    NoticeLogContext noticeLogContext, Result<Response> result, Throwable throwable) {

    PatronNoticeLogHelper.logResponse(result, throwable, HTTP_OK.toInt(), patronNotice);

    return eventPublisher.publishNoticeLogEvent(noticeLogContext, result, throwable);
  }

}
