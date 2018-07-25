package org.folio.circulation.domain;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;

import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.HttpResult.*;

public class UpdateItem {

  private final CollectionResourceClient itemsStorageClient;

  public UpdateItem(Clients clients) {
    itemsStorageClient = clients.itemsStorage();
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> onCheckOut(
    LoanAndRelatedRecords relatedRecords) {

    RequestQueue requestQueue = relatedRecords.getRequestQueue();

    //Hack for creating returned loan - should distinguish further up the chain
    return succeeded(relatedRecords).afterWhen(
      records -> loanIsClosed(relatedRecords),
      UpdateItem::skip,
      records -> updateItemStatusOnCheckOut(relatedRecords, requestQueue));
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> onLoanUpdate(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return HttpResult.of(() -> itemStatusOnLoanUpdate(
        loanAndRelatedRecords.getLoan(), loanAndRelatedRecords.getRequestQueue()))
      .after(prospectiveStatus ->
        updateItemWhenNotSameStatus(loanAndRelatedRecords,
          loanAndRelatedRecords.getLoan().getItem(), prospectiveStatus));
  }

  CompletableFuture<HttpResult<RequestAndRelatedRecords>> onRequestCreation(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return HttpResult.of(() -> itemStatusOnRequestCreation(requestAndRelatedRecords))
      .after(prospectiveStatus ->
        updateItemWhenNotSameStatus(requestAndRelatedRecords,
          requestAndRelatedRecords.getRequest().getItem(), prospectiveStatus));
  }

  private CompletableFuture<HttpResult<LoanAndRelatedRecords>> updateItemStatusOnCheckOut(
    LoanAndRelatedRecords loanAndRelatedRecords,
    RequestQueue requestQueue) {

    return HttpResult.of(requestQueue::checkedOutItemStatus)
      .after(prospectiveStatus ->
        updateItemWhenNotSameStatus(loanAndRelatedRecords,
          loanAndRelatedRecords.getLoan().getItem(), prospectiveStatus));
  }

  private <T> CompletableFuture<HttpResult<T>> updateItemWhenNotSameStatus(
    T relatedRecords,
    Item item,
    ItemStatus prospectiveStatus) {

    return updateItemWhenNotSameStatus(prospectiveStatus, item)
      .thenApply(result -> result.map(v -> relatedRecords));
  }

  private CompletableFuture<HttpResult<Item>> updateItemWhenNotSameStatus(
    ItemStatus prospectiveStatus,
    Item item) {

    if(item.isNotSameStatus(prospectiveStatus)) {
      item.changeStatus(prospectiveStatus);

      return this.itemsStorageClient.put(item.getItemId(),
        item.getItem()).thenApply(putItemResponse -> {
          if(putItemResponse.getStatusCode() == 204) {
            return succeeded(item);
          }
          else {
            return failed(new ServerErrorFailure(
              String.format("Failed to update item status '%s'",
                putItemResponse.getBody())));
          }
        });
    }
    else {
      return completedFuture(succeeded(item));
    }
  }

  private CompletableFuture<HttpResult<Boolean>> loanIsClosed(
    LoanAndRelatedRecords relatedRecords) {

    return completedFuture(of(() -> relatedRecords.getLoan().isClosed()));
  }

  private static <T> CompletableFuture<HttpResult<T>> skip(T previousResult) {
    return completedFuture(succeeded(previousResult));
  }

  private ItemStatus itemStatusOnRequestCreation(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    RequestQueue requestQueue = requestAndRelatedRecords.getRequestQueue();

    return requestQueue.hasOutstandingRequests()
      ? requestQueue.getHighestPriorityRequest().checkedOutItemStatus()
      : requestAndRelatedRecords.getRequest().checkedOutItemStatus();
  }

  private ItemStatus itemStatusOnLoanUpdate(
    Loan loan,
    RequestQueue requestQueue) {

    return loan.isClosed()
      ? requestQueue.checkedInItemStatus()
      : requestQueue.checkedOutItemStatus();
  }
}
