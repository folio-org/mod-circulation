package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.Objects;
import java.util.function.Predicate;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.reorder.ReorderRequest;
import org.folio.circulation.resources.context.ReorderRequestContext;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.request.RequestHelper;

public class RequestQueueValidation {

  private RequestQueueValidation() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static Result<ReorderRequestContext> queueFoundForItem(
    Result<ReorderRequestContext> result) {
    return result.failWhen(
      r -> Result.succeeded(r.getRequestQueue().getRequests().isEmpty()),
      r -> new RecordNotFoundFailure("Item", r.getItemId())
    );
  }

  public static Result<ReorderRequestContext> queueIsConsistent(Result<ReorderRequestContext> result) {
    return result.failWhen(
      context -> Result.succeeded(isQueueInconsistent(context)),
      context -> singleValidationError("There is inconsistency between provided reordered queue and item queue.", null, null)
    );
  }

  public static Result<ReorderRequestContext> fulfillingRequestHasFirstPosition(
    Result<ReorderRequestContext> result) {
    return validateRequestAtFirstPosition(result, RequestHelper::isRequestBeganFulfillment,
      "Requests can not be displaced from position 1 when fulfillment begun.");
  }

  public static Result<ReorderRequestContext> pageRequestHasFirstPosition(Result<ReorderRequestContext> result) {
    return validateRequestAtFirstPosition(result, RequestHelper::isPageRequest,
      "Page requests can not be displaced from position 1.");
  }

  private static boolean isQueueInconsistent(ReorderRequestContext context) {
    if (context.getReorderQueueRequest().getReorderedQueue().size() !=
      context.getRequestQueue().getRequests().size()) {
      return true;
    }

    // Check whether an reordered request did not match with request in the queue.
    return context.getReorderRequestToRequestMap().values().stream()
      .anyMatch(Objects::isNull);
  }

  private static Result<ReorderRequestContext> validateRequestAtFirstPosition(
    Result<ReorderRequestContext> result,
    Predicate<Request> requestTypePredicate,
    String onFailureMessage) {

    return result.failWhen(context -> {
        boolean notAtFirstPosition = context.getReorderRequestToRequestMap()
          .entrySet().stream()
          .anyMatch(entry -> {
            ReorderRequest reorderRequest = entry.getKey();
            Request request = entry.getValue();

            return requestTypePredicate.test(request)
              && reorderRequest.getNewPosition() != 1;
          });

        return Result.succeeded(notAtFirstPosition);
      },
      r -> singleValidationError(onFailureMessage, "position", null)
    );
  }
}
