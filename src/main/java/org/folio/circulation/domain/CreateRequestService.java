package org.folio.circulation.domain;

import org.folio.circulation.support.HttpResult;

import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.HttpResult.succeeded;

public class CreateRequestService {
  private final RequestRepository requestRepository;
  private final UpdateItem updateItem;
  private final UpdateLoanActionHistory updateLoanActionHistory;

  public CreateRequestService(
    RequestRepository requestRepository,
    UpdateItem updateItem,
    UpdateLoanActionHistory updateLoanActionHistory) {

    this.requestRepository = requestRepository;
    this.updateItem = updateItem;
    this.updateLoanActionHistory = updateLoanActionHistory;
  }


  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> createRequest(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return setRequestQueuePosition(requestAndRelatedRecords)
      .thenComposeAsync(r -> r.after(updateItem::onRequestCreation))
      .thenComposeAsync(r -> r.after(updateLoanActionHistory::onRequestCreation))
      .thenComposeAsync(r -> r.after(requestRepository::create));
  }

  private static CompletableFuture<HttpResult<RequestAndRelatedRecords>> setRequestQueuePosition(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    //TODO: Extract to method to add to queue
    requestAndRelatedRecords.withRequest(requestAndRelatedRecords.getRequest()
      .changePosition(requestAndRelatedRecords.getRequestQueue().nextAvailablePosition()));

    return completedFuture(succeeded(requestAndRelatedRecords));
  }
}
