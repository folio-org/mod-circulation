package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.reorder.ReorderRequest;
import org.folio.circulation.resources.context.ReorderRequestContext;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.request.RequestHelper;

public class RequestQueueValidation {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private RequestQueueValidation() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static Result<ReorderRequestContext> queueIsFound(Result<ReorderRequestContext> result) {
    log.debug("queueIsFound:: parameters result={}", () -> resultAsString(result));

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
    log.debug("queueIsConsistent:: parameters result={}", () -> resultAsString(result));

    return result.failWhen(
      context -> succeeded(isQueueInconsistent(context)),
      context -> singleValidationError(
        "There is inconsistency between provided reordered queue and existing queue.",
        null, null)
    );
  }

  public static Result<ReorderRequestContext> fulfillingRequestsPositioning(
    Result<ReorderRequestContext> result) {

    log.debug("fulfillingRequestsPositioning:: parameters result={}", () -> resultAsString(result));

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

    log.debug("fulfillingRequestsHaveTopPositions:: parameters result={}",
      () -> resultAsString(result));

    return validateRequestsAtTopPositions(result, RequestHelper::isRequestBeganFulfillment,
      "Requests can not be displaced from top positions when fulfillment begun.");
  }

  private static Result<ReorderRequestContext> fulfillingRequestHasFirstPosition(
    Result<ReorderRequestContext> result) {

    log.debug("fulfillingRequestHasFirstPosition:: parameters result={}",
      () -> resultAsString(result));

    return validateRequestAtFirstPosition(result, RequestHelper::isRequestBeganFulfillment,
      "Requests can not be displaced from position 1 when fulfillment begun.");
  }

  public static Result<ReorderRequestContext> pageRequestsPositioning(
    Result<ReorderRequestContext> result) {

    log.debug("pageRequestsPositioning:: parameters result={}",
      () -> resultAsString(result));

    if (result.failed()) {
      log.info("pageRequestsPositioning:: failed result, exiting");
      return result;
    }

    return result.value().isQueueForInstance()
      ? result
      : pageRequestHasFirstPosition(result);
  }

  private static Result<ReorderRequestContext> pageRequestHasFirstPosition(
    Result<ReorderRequestContext> result) {

    log.debug("pageRequestHasFirstPosition:: parameters result={}",
      () -> resultAsString(result));

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
    log.debug("positionsAreSequential:: parameters result={}",
      () -> resultAsString(result));

    return result.failWhen(
      r -> {
        log.debug("positionsAreSequential:: failWhen");
        List<ReorderRequest> sortedReorderedQueue = r.getReorderQueueRequest()
          .getReorderedQueue().stream()
          .sorted(Comparator.comparingInt(ReorderRequest::getNewPosition))
          .collect(Collectors.toList());

        int expectedCurrentPosition = 1;
        for (ReorderRequest reorderRequest : sortedReorderedQueue) {
          log.debug("positionsAreSequential:: processing reorderRequest {}", reorderRequest);
          if (reorderRequest.getNewPosition() != expectedCurrentPosition) {
            log.debug("positionsAreSequential:: reorderRequest.newPosition != {}, exiting",
              expectedCurrentPosition);
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
    log.debug("isQueueInconsistent:: parameters context={}", context);

    if (context.getReorderQueueRequest().getReorderedQueue().size() !=
      context.getRequestQueue().getRequests().size()) {
      log.debug("isQueueInconsistent:: reordered queue size is not equal to number of requests");
      return true;
    }

    return reorderRequestContainsUnmatchedRequests(context);
  }

  private static boolean reorderRequestContainsUnmatchedRequests(ReorderRequestContext context) {
    log.debug("reorderRequestContainsUnmatchedRequests:: parameters context={}", context);
    var result = context.getReorderRequestToRequestMap().entrySet().stream()
      .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null);
    log.info("reorderRequestContainsUnmatchedRequests:: result {}", result);
    return result;
  }

  private static Result<ReorderRequestContext> validateRequestAtFirstPosition(
    Result<ReorderRequestContext> result, Predicate<Request> requestTypePredicate,
    String onFailureMessage) {

    log.debug("validateRequestAtFirstPosition:: parameters result={}, requestTypePredicate, " +
        "onFailureMessage={}", () -> resultAsString(result), () -> onFailureMessage);

    return result.failWhen(context -> {
        log.debug("validateRequestAtFirstPosition:: failWhen");
        boolean notAtFirstPosition = context.getReorderRequestToRequestMap()
          .entrySet().stream()
          .anyMatch(entry -> {
            log.debug("validateRequestAtFirstPosition:: matching reorder request");
            ReorderRequest reorderRequest = entry.getKey();
            Request request = entry.getValue();

            var matchResult = requestTypePredicate.test(request)
              && reorderRequest.getNewPosition() != 1;
            log.debug("validateRequestAtFirstPosition:: result {}", matchResult);
            return matchResult;
          });

        return succeeded(notAtFirstPosition);
      },
      r -> singleValidationError(onFailureMessage, "newPosition", null)
    );
  }

  private static Result<ReorderRequestContext> validateRequestsAtTopPositions(
    Result<ReorderRequestContext> result, Predicate<Request> requestTypePredicate,
    String onFailureMessage) {

    log.debug("validateRequestsAtTopPositions:: parameters result={}, requestTypePredicate, " +
      "onFailureMessage={}", () -> resultAsString(result), () -> onFailureMessage);

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
