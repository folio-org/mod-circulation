package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.RequestStatus.OPEN_NOT_YET_FILLED;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestRepository;
import org.folio.circulation.support.Result;

public class BlockRenewalValidator {
  private final RequestRepository requestRepository;

  public BlockRenewalValidator(RequestRepository requestRepository) {
    this.requestRepository = requestRepository;
  }

  public CompletableFuture<Result<Loan>> refuseWhenFirstRequestIsRecall(Loan loan) {
    return refuseWhenFirstRequestIsRecall(loan.getItem())
      .thenApply(r -> r.map(item -> loan));
  }

  public CompletableFuture<Result<Item>> refuseWhenFirstRequestIsRecall(Item item) {
    return requestRepository.findRequestOnTheFirstPosition(item)
      .thenApply(r -> r.next(request -> {
        if (request != null && isRecallRequest(request)) {
          String reason = "Items cannot be renewed when there is an active recall request";
          return failedValidation(reason, "request id", request.getId());
        }
        return succeeded(item);
      }));
  }

  private boolean isRecallRequest(Request request) {
    return request.getPosition() == 1
      && request.getRequestType() == RECALL
      && request.getStatus() == OPEN_NOT_YET_FILLED;
  }
}
