package org.folio.circulation.domain;
import org.folio.circulation.support.HttpResult;
import java.util.concurrent.CompletableFuture;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.HttpResult.succeeded;

public class UpdateRequest {

  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> updateRequestOnCreation(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    Request request = requestAndRelatedRecords.getRequest();

    if (request.getRequestType() == RequestType.PAGE) {
      request.changeStatus(RequestStatus.OPEN_NOT_YET_FILLED);
    }

    return completedFuture(succeeded(requestAndRelatedRecords));
  }
}
