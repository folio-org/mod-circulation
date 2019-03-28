package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestRepository;
import org.folio.circulation.support.Result;

public class ClosedRequestValidator {
  private final RequestRepository requestRepository;

  public ClosedRequestValidator(RequestRepository requestRepository) {
    this.requestRepository = requestRepository;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> refuseWhenAlreadyClosed(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return refuseWhenAlreadyClosed(requestAndRelatedRecords.getRequest())
      .thenApply(r -> r.map(v -> requestAndRelatedRecords));
  }

  private CompletableFuture<Result<Request>> refuseWhenAlreadyClosed(Request request) {
    final String requestId = request.getId();

    return requestRepository.getById(requestId)
      .thenApply(r -> r.failWhen(existing -> succeeded(existing.isClosed()),
        v -> singleValidationError("Cannot edit a closed request", "id", requestId)));
  }

}
