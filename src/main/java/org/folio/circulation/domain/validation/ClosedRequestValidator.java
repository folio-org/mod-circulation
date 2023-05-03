package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.ErrorCode.REQUEST_ALREADY_CLOSED;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.support.results.Result;

public class ClosedRequestValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final RequestRepository requestRepository;

  public ClosedRequestValidator(RequestRepository requestRepository) {
    this.requestRepository = requestRepository;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> refuseWhenAlreadyClosed(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("refuseWhenAlreadyClosed:: parameters requestAndRelatedRecords={}",
      requestAndRelatedRecords);

    return refuseWhenAlreadyClosed(requestAndRelatedRecords.getRequest())
      .thenApply(r -> r.map(v -> requestAndRelatedRecords));
  }

  private CompletableFuture<Result<Request>> refuseWhenAlreadyClosed(Request request) {
    log.debug("refuseWhenAlreadyClosed:: parameters request={}", request);

    final String requestId = request.getId();

    return requestRepository.getById(requestId)
      .thenApply(r -> r.failWhen(existing -> succeeded(existing.isClosed()),
        v -> singleValidationError("The Request has already been closed", "id", requestId,
          REQUEST_ALREADY_CLOSED)));
  }

}
