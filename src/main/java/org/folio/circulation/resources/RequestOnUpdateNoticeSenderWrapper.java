package org.folio.circulation.resources;

import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.support.results.Result;

public class RequestOnUpdateNoticeSenderWrapper {
  private final RequestNoticeSender requestNoticeSender;
  private final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public RequestOnUpdateNoticeSenderWrapper(RequestNoticeSender requestNoticeSender) {
    this.requestNoticeSender = requestNoticeSender;
  }

  public Result<RequestAndRelatedRecords> sendOnUpd(RequestAndRelatedRecords requestAndRelatedRecords) {
    Request request = requestAndRelatedRecords.getRequest();
    Request originalRequest = requestAndRelatedRecords.getOriginalRequest();
    log.info("ANTON:: replaceRequest:: parameters requestAndRelatedRecords: {}",
      requestAndRelatedRecords);
    if(true) {
      requestNoticeSender.sendNoticeOnRequestCreated(requestAndRelatedRecords);
    }
    return requestNoticeSender.sendNoticeOnRequestUpdated(requestAndRelatedRecords);
//    return succeeded(requestAndRelatedRecords);

  }
}
