package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.RequestStatus.OPEN_NOT_YET_FILLED;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.support.HttpResult.of;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

public class BlockRenewalValidator {
  private final RequestQueueRepository requestQueueRepository;

  public BlockRenewalValidator(RequestQueueRepository requestQueueRepository) {

    this.requestQueueRepository = requestQueueRepository;
  }

  public CompletableFuture<HttpResult<Item>> refuseWhenFirstRequestIsRecall(Item item) {

    return requestQueueRepository.get(item.getItemId())
      .thenApply(result -> result.combineToResult(of(() -> item), this::checkAndCombine));
  }

  private HttpResult<Item> checkAndCombine(RequestQueue requestQueue, Item item) {
    Optional<Request> request = requestQueue.getRequests()
      .stream()
      .min(Comparator.comparing(Request::getPosition));

    if (request.isPresent() && isRecallRequest(request.get())) {
      String reason = "Items cannot be renewed when there is an active recall request";


      ValidationErrorFailure error = failure(reason, "request id", request.get().getId());
      return HttpResult.failed(error);
    } else {
      return succeeded(item);
    }
  }


  private boolean isRecallRequest(Request request) {

    return request.getPosition() == 1
      && request.getRequestType() == RECALL
      && request.getStatus() == OPEN_NOT_YET_FILLED;
  }


}
