package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

  /**
   * Verifies that provided reordered queue has exact the same requests that currently present
   * in the DB. No new requests added to neither reorderedQueue nor actual queue in DB.
   *
   * @param result - Context.
   * @return New result, failed when the validation has not been passed.
   */
  public static Result<ReorderRequestContext> queueIsConsistent(Result<ReorderRequestContext> result) {
    return result.failWhen(
      context -> Result.succeeded(isQueueInconsistent(context)),
      context -> singleValidationError(
        "There is inconsistency between provided reordered queue and item queue.",
        null, null)
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

  /**
   * Verifies that newPositions has sequential order, i.e. 1, 2, 3, 4
   * but not 1, 2, 3, 40.
   *
   * @param result - Context object.
   * @return New Result, failed if validation have not been passed.
   */
  public static Result<ReorderRequestContext> positionsAreSequential(Result<ReorderRequestContext> result) {
    return result.failWhen(
      r -> {
        List<ReorderRequest> sortedReorderedQueue = r.getReorderQueueRequest()
          .getReorderedQueue().stream()
          .sorted(Comparator.comparingInt(ReorderRequest::getNewPosition))
          .collect(Collectors.toList());

        int expectedCurrentPosition = 1;
        for (ReorderRequest reorderRequest : sortedReorderedQueue) {
          if (reorderRequest.getNewPosition() != expectedCurrentPosition) {
            return Result.succeeded(true);
          }

          expectedCurrentPosition++;
        }

        return Result.succeeded(false);
      },
      r -> singleValidationError("Positions must have sequential order.", "newPosition", null)
    );
  }

  private static boolean isQueueInconsistent(ReorderRequestContext context) {
    if (context.getReorderQueueRequest().getReorderedQueue().size() !=
      context.getRequestQueue().getRequests().size()) {
      return true;
    }

    return reorderRequestContainsUnmatchedRequests(context);
  }

  private static boolean reorderRequestContainsUnmatchedRequests(
    ReorderRequestContext context) {

    return context.getReorderRequestToRequestMap().entrySet().stream()
      .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null);
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
      r -> singleValidationError(onFailureMessage, "newPosition", null)
    );
  }
}
