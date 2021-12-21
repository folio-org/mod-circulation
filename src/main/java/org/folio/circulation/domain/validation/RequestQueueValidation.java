package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.reorder.ReorderRequest;
import org.folio.circulation.resources.context.ReorderRequestContext;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.request.RequestHelper;

public class RequestQueueValidation {

  private RequestQueueValidation() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static Result<ReorderRequestContext> queueIsFound(
    Result<ReorderRequestContext> result) {

    return result.failWhen(
      r -> succeeded(r.getRequestQueue().getRequests().isEmpty()),
      r -> new RecordNotFoundFailure(r.isQueueForInstance() ? "Instance" : "Item",
        r.getIdParamValue()));
  }

  /**
   * Verifies that provided reordered queue has exact the same requests that currently present
   * in the DB. No new requests added to neither reorderedQueue nor actual queue in DB.
   * Used for both item and instance queues.
   *
   * @param result - Context.
   * @return New result, failed when the validation has not been passed.
   */
  public static Result<ReorderRequestContext> queueIsConsistent(Result<ReorderRequestContext> result) {
    return result.failWhen(
      context -> succeeded(isQueueInconsistent(context)),
      context -> singleValidationError(
        "There is inconsistency between provided reordered queue and existing queue.",
        null, null)
    );
  }

  public static Result<ReorderRequestContext> fulfillingRequestsPositioning(
    Result<ReorderRequestContext> result) {

    if (result.failed()) {
      return result;
    }

    return result.value().isQueueForInstance()
      ? fulfillingRequestsHaveTopPositions(result)
      : fulfillingRequestHasFirstPosition(result);
  }

  /**
   * Makes sure that all requests that are in the process of fulfilment are always at the top of
   * the unified queue (TLR feature enabled).
   *
   * @param result - Context
   * @return New result, failed if validation has failed
   */
  private static Result<ReorderRequestContext> fulfillingRequestsHaveTopPositions(
    Result<ReorderRequestContext> result) {
    return validateRequestsAtTopPositions(result, RequestHelper::isRequestBeganFulfillment,
      "Requests can not be displaced from top positions when fulfillment begun.");
  }

  private static Result<ReorderRequestContext> fulfillingRequestHasFirstPosition(
    Result<ReorderRequestContext> result) {
    return validateRequestAtFirstPosition(result, RequestHelper::isRequestBeganFulfillment,
      "Requests can not be displaced from position 1 when fulfillment begun.");
  }

  public static Result<ReorderRequestContext> pageRequestsPositioning(
    Result<ReorderRequestContext> result) {

    if (result.failed()) {
      return result;
    }

    return pageRequestHasFirstPosition(result);
  }

  /**
   * Makes sure that all Page requests are always at the first position of the unified queue.
   *
   * @param result - Context
   * @return New result, failed if validation has failed
   */
  private static Result<ReorderRequestContext> pageRequestHasFirstPosition(Result<ReorderRequestContext> result) {
    return validateRequestAtFirstPosition(result, RequestHelper::isPageRequest,
      "Page requests can not be displaced from position 1.");
  }

  /**
   * Verifies that newPositions has sequential order, i.e. 1, 2, 3, 4 but not 1, 2, 3, 40.
   * Used for both item and instance queues.
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
            return succeeded(true);
          }

          expectedCurrentPosition++;
        }

        return succeeded(false);
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

        return succeeded(notAtFirstPosition);
      },
      r -> singleValidationError(onFailureMessage, "newPosition", null)
    );
  }

  private static Result<ReorderRequestContext> validateRequestsAtTopPositions(
    Result<ReorderRequestContext> result,
    Predicate<Request> requestTypePredicate,
    String onFailureMessage) {

    return result.failWhen(context -> {
        List<Integer> newPositions = context.getReorderRequestToRequestMap()
          .entrySet()
          .stream()
          .filter(entry -> requestTypePredicate.test(entry.getValue()))
          .map(entry -> entry.getKey().getNewPosition())
          .sorted()
          .collect(Collectors.toList());

        return succeeded(IntStream.range(0, newPositions.size())
          .anyMatch(i -> newPositions.get(i) != i + 1));
      },
      r -> singleValidationError(onFailureMessage, "newPosition", null)
    );
  }

}
