package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.HttpResult;

public class CreateRequestService {
  private final RequestRepository requestRepository;
  private final UpdateItem updateItem;
  private final UpdateLoanActionHistory updateLoanActionHistory;
  private final UpdateLoan updateLoan;

  public CreateRequestService(
    RequestRepository requestRepository,
    UpdateItem updateItem,
    UpdateLoanActionHistory updateLoanActionHistory,
    UpdateLoan updateLoan) {

    this.requestRepository = requestRepository;
    this.updateItem = updateItem;
    this.updateLoanActionHistory = updateLoanActionHistory;
    this.updateLoan = updateLoan;
  }

  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> createRequest(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return completedFuture(refuseWhenItemIsNotValid(requestAndRelatedRecords)
      .next(CreateRequestService::refuseWhenItemDoesNotExist)
      .map(CreateRequestService::setRequestQueuePosition))
      .thenComposeAsync(r -> r.after(updateItem::onRequestCreation))
      .thenComposeAsync(r -> r.after(updateLoanActionHistory::onRequestCreation))
      .thenComposeAsync(r -> r.after(updateLoan::onRequestCreation))
      .thenComposeAsync(r -> r.after(requestRepository::create));
  }

  private static RequestAndRelatedRecords setRequestQueuePosition(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    //TODO: Extract to method to add to queue
    requestAndRelatedRecords.withRequest(requestAndRelatedRecords.getRequest()
      .changePosition(requestAndRelatedRecords.getRequestQueue().nextAvailablePosition()));

    return requestAndRelatedRecords;
  }

  private static HttpResult<RequestAndRelatedRecords> refuseWhenItemDoesNotExist(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    if(requestAndRelatedRecords.getRequest().getItem().isNotFound()) {
      return failed(failure(
        "Item does not exist", "itemId",
        requestAndRelatedRecords.getRequest().getItemId()));
    }
    else {
      return succeeded(requestAndRelatedRecords);
    }
  }

  private static HttpResult<RequestAndRelatedRecords> refuseWhenItemIsNotValid(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    Request request = requestAndRelatedRecords.getRequest();

    if (!request.allowedForItem()) {
      return failed(failure(
        String.format("Item is %s", request.getItem().getStatus()),
        "itemId", request.getItemId()
      ));
    }
    else {
      return succeeded(requestAndRelatedRecords);
    }
  }
}
