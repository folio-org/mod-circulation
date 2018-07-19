package org.folio.circulation.domain.validation;

import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestRepository;
import org.folio.circulation.support.HttpResult;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

public class ClosedRequestValidator {
  private final RequestRepository requestRepository;

  public ClosedRequestValidator(RequestRepository requestRepository) {
    this.requestRepository = requestRepository;
  }

  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> refuseWhenAlreadyClosed(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final String requestId = requestAndRelatedRecords.getRequest().getId();

    return requestRepository.getById(requestId)
      .thenApply(r2 -> r2.next(existingRepresentation -> {
        if (existingRepresentation.isClosed()) {
          return failed(failure(
            "Cannot edit a closed request", "id", requestId));
        } else {
          return succeeded(requestAndRelatedRecords);
        }
    }));
  }
}
