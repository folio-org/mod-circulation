package org.folio.circulation.services;

import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.folio.circulation.support.results.Result.emptyAsync;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.lang.invoke.MethodHandles;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestPolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RequestQueueService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final RequestPolicyRepository requestPolicyRepository;
  private final LoanPolicyRepository loanPolicyRepository;

  public static RequestQueueService using(Clients clients) {
    return new RequestQueueService(
      new RequestPolicyRepository(clients),
      new LoanPolicyRepository(clients)
    );
  }

  public CompletableFuture<Result<Request>> findRequestFulfillableByItem(Item item,
    RequestQueue requestQueue) {

    log.info("findRequestFulfillableByItem:: parameters itemId: {}", item::getItemId);

    // MCBFF-211 diagnostics: full ordered list of "fulfillable candidate" requests for this
    // item, in the order they will be evaluated (i.e. current position order). This is the
    // queue snapshot at the moment checkout/checkin selection happens - compare the order
    // here against what the Central-tenant ECS sync (mod-tlr RequestBatchUpdateEventHandler)
    // later computes, to see if/when the two diverge.
    requestQueue.fulfillableRequests().forEach(r -> log.info(
      "MCBFF-211 findRequestFulfillableByItem:: candidate -> id: {}, itemId: {}, " +
        "instanceId: {}, requestLevel: {}, requestType: {}, position: {}, status: {}",
      r.getId(), r.getItemId(), r.getInstanceId(), r.getRequestLevel(), r.getRequestType(),
      r.getPosition(), r.getStatus()));

    return findRequestFulfillableByItem(item, requestQueue.fulfillableRequests().iterator());
  }

  private CompletableFuture<Result<Request>> findRequestFulfillableByItem(Item item,
    Iterator<Request> iterator) {

    if (!iterator.hasNext()) {
      return emptyAsync();
    }

    final Request request = iterator.next();

    return isRequestFulfillableByItem(item, request)
      .thenCompose(r -> r.after(whenTrue(logSelectedAndReturn(request), findRequestFulfillableByItem(item, iterator))));
  }

  // MCBFF-211 diagnostics: log which request actually WON the fulfillability check and got
  // selected to be closed/fulfilled by this checkout/checkin - this is the "who gets the
  // item" decision, distinct from queue resequencing/reordering afterwards.
  private CompletableFuture<Result<Request>> logSelectedAndReturn(Request request) {
    log.info("MCBFF-211 findRequestFulfillableByItem:: SELECTED request -> id: {}, itemId: {}, " +
        "instanceId: {}, requestLevel: {}, requestType: {}, position: {}",
      request.getId(), request.getItemId(), request.getInstanceId(), request.getRequestLevel(),
      request.getRequestType(), request.getPosition());
    return ofAsync(request);
  }

  public CompletableFuture<Result<Boolean>> isRequestFulfillableByItem(Item item, Request request) {
    log.info("isRequestFulfillableByItem:: parameters itemId: {}, requestId: {}, level: {}",
      item::getItemId, request::getId, request::getRequestLevel);
    switch (request.getRequestLevel()) {
      case ITEM:
        return isItemLevelRequestFulfillableByItem(item, request);
      case TITLE:
        return isTitleLevelRequestFulfillableByItem(item, request);
      default:
        log.info("isRequestFulfillableByItem:: unknown request level: {}", request::getRequestLevel);
        return ofAsync(false);
    }
  }

  private CompletableFuture<Result<Boolean>> isItemLevelRequestFulfillableByItem(Item item,
    Request request) {

    log.info("isItemLevelRequestFulfillableByItem:: parameters itemId: {}, requestId: {}",
      item::getItemId, request::getId);
    return ofAsync(StringUtils.equals(item.getItemId(), request.getItemId()));
  }

  protected CompletableFuture<Result<Boolean>> isTitleLevelRequestFulfillableByItem(Item item,
    Request request) {

    log.info("isTitleLevelRequestFulfillableByItem:: parameters itemId: {}, requestId: {}",
      item::getItemId, request::getId);

    if (!StringUtils.equals(request.getItemId(), item.getItemId()) &&
      !StringUtils.equals(request.getInstanceId(), item.getInstanceId())) {
      log.info("isTitleLevelRequestFulfillableByItem:: itemId and instanceId mismatch, not fulfillable");
      log.info("MCBFF-211 isTitleLevelRequestFulfillableByItem(base):: requestId: {} -> " +
        "NOT fulfillable (itemId/instanceId mismatch)", request.getId());
      return ofAsync(false);
    }

    if (request.isRecall() && request.isNotYetFilled()) {
      log.info("isTitleLevelRequestFulfillableByItem:: recall request not yet filled, checking requestable and loanable");
      log.info("MCBFF-211 isTitleLevelRequestFulfillableByItem(base):: requestId: {} is a " +
        "RECALL, using isItemRequestableAndLoanable path (not simple itemId match)",
        request.getId());
      return isItemRequestableAndLoanable(item, request);
    }

    return canRequestBeFulfilledByItem(item, request);
  }

  protected CompletableFuture<Result<Boolean>> canRequestBeFulfilledByItem(Item item,
    Request request) {

    log.info("canRequestBeFulfilledByItem:: parameters itemId: {}, requestId: {}",
      item::getItemId, request::getId);
    return request.getItemId() == null ^ StringUtils.equals(item.getItemId(), request.getItemId())
      ? isItemRequestableAndLoanable(item, request)
      : ofAsync(false);
  }

  protected CompletableFuture<Result<Boolean>> isItemRequestableAndLoanable(Item item, Request request) {
    log.info("isItemRequestableAndLoanable:: parameters itemId: {}, requestId: {}",
      item::getItemId, request::getId);
    return isItemRequestable(item, request)
      .thenCompose(r -> r.after(whenTrue(isItemLoanable(item, request), ofAsync(false))));
  }

  private CompletableFuture<Result<Boolean>> isItemRequestable(Item item, Request request) {
    return Optional.ofNullable(request.getInstanceItemsRequestPolicies())
      .map(policies -> policies.get(item.getItemId()))
      .map(Result::ofAsync)
      .orElseGet(() -> requestPolicyRepository.lookupRequestPolicy(item, request.getRequester()))
      .thenApply(r -> r.map(policy -> policy.allowsType(request.getRequestType())));
  }

  private CompletableFuture<Result<Boolean>> isItemLoanable(Item item, Request request) {
    return loanPolicyRepository.lookupPolicy(item, request.getRequester())
      .thenApply(r -> r.map(LoanPolicy::isLoanable));
  }

  public CompletableFuture<Result<Boolean>> isItemRequestedByAnotherPatron(
    RequestQueue requestQueue, User requester, Item item) {

    log.info("isItemRequestedByAnotherPatron:: parameters itemId: {}", item::getItemId);
    return findRequestFulfillableByItem(item, requestQueue)
      .thenApply(r -> r.map(request -> !(request == null || request.isFor(requester))));
  }

  private static <T> Function<Boolean, CompletableFuture<Result<T>>> whenTrue(
    CompletableFuture<Result<T>> action, CompletableFuture<Result<T>> otherwise) {

    return predicate -> isTrue(predicate) ? action : otherwise;
  }
}
