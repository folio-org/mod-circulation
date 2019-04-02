package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.RequestStatus.OPEN_NOT_YET_FILLED;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.support.Result;

public class BlockRenewalValidator {
  private final RequestQueueRepository requestQueueRepository;

  public BlockRenewalValidator(RequestQueueRepository requestQueueRepository) {
    this.requestQueueRepository = requestQueueRepository;
  }

  public CompletableFuture<Result<Item>> refuseWhenFirstRequestIsRecall(Item item) {
    return requestQueueRepository.get(item.getItemId())
      .thenApply(result -> result.combineToResult(of(() -> item), this::checkAndCombine));
  }

  private Result<Item> checkAndCombine(RequestQueue requestQueue, Item item) {
    Optional<Request> request = requestQueue.getRequests()
      .stream()
      .min(Comparator.comparing(Request::getPosition));

    if (request.isPresent() && isRecallRequest(request.get())) {
      String reason = "Items cannot be renewed when there is an active recall request";

      return failedValidation(reason, "request id", request.get().getId());
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
