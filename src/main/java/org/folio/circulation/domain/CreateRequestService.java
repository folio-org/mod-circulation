package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.policy.RequestPolicy;
import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.support.HttpResult;

public class CreateRequestService {
  private final RequestRepository requestRepository;
  private final UpdateItem updateItem;
  private final UpdateLoanActionHistory updateLoanActionHistory;
  private final RequestPolicyRepository requestPolicyRepository;

  public CreateRequestService(
    RequestRepository requestRepository,
    UpdateItem updateItem,
    UpdateLoanActionHistory updateLoanActionHistory,
    RequestPolicyRepository requestPolicyRepository) {

    this.requestRepository = requestRepository;
    this.updateItem = updateItem;
    this.updateLoanActionHistory = updateLoanActionHistory;
    this.requestPolicyRepository = requestPolicyRepository;
  }

  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> createRequest(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return completedFuture(refuseWhenItemIsNotValid(requestAndRelatedRecords)
      .next(CreateRequestService::refuseWhenItemDoesNotExist)
      .next(CreateRequestService::refuseWhenInvalidUserAndPatronGroup))
      .thenComposeAsync( r-> r.after(requestPolicyRepository::lookupRequestPolicy)) //get policy
      .thenApply( r -> r.next(CreateRequestService::refuseWhenRequestCannotBeFulfilled)) //check policy here
      .thenApply(r -> r.map(CreateRequestService::setRequestQueuePosition))
      .thenComposeAsync(r -> r.after(updateItem::onRequestCreation))
      .thenComposeAsync(r -> r.after(updateLoanActionHistory::onRequestCreation))
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

  private static HttpResult<RequestAndRelatedRecords> refuseWhenRequestCannotBeFulfilled(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    RequestPolicy requestPolicy = requestAndRelatedRecords.getRequestPolicy();
    RequestType requestType =  requestAndRelatedRecords.getRequest().getRequestType();

    if(!requestPolicy.allowsType(requestType)) {
      return failed(failure(
        requestType.getValue() + " requests are not allowed for this patron and item combination", "requestType",
        requestType.getValue()));
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
        String.format("%s requests are not allowed for %s item status combination", request.getRequestType().getValue() , request.getItem().getStatus().getValue()),
        request.getRequestType().getValue(),
        request.getItemId()
      ));
    }
    else {
      return succeeded(requestAndRelatedRecords);
    }
  }

  private static HttpResult<RequestAndRelatedRecords> refuseWhenInvalidUserAndPatronGroup(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    Request request = requestAndRelatedRecords.getRequest();
    User requester = request.getRequester();

    if (requester == null){
      return failed(failure(
        "A valid user and patron group are required. User is null",
        "User", null
      ));
    } else if (requester.getPatronGroupId() == null) {
      return failed(failure(
        "A valid patron group is required. PatronGroup ID is null",
        "PatronGroupId", null
      ));
    }
    else {
      return succeeded(requestAndRelatedRecords);
    }
  }
}
