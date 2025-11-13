package org.folio.circulation.services;

import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestFulfillmentPreference;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class RequestAnonymizationService {
  private static final Set<RequestStatus> ALLOWED_STATUSES = EnumSet.of(
    RequestStatus.CLOSED_FILLED,
    RequestStatus.CLOSED_CANCELLED,
    RequestStatus.CLOSED_PICKUP_EXPIRED,
    RequestStatus.CLOSED_UNFILLED
  );

  private static final Set<RequestStatus> OPEN_STATUSES = EnumSet.of(
    RequestStatus.OPEN_AWAITING_PICKUP,
    RequestStatus.OPEN_IN_TRANSIT,
    RequestStatus.OPEN_NOT_YET_FILLED
  );

  private final RequestRepository requestRepository;
  private final EventPublisher eventPublisher;

  public RequestAnonymizationService(Clients clients, EventPublisher eventPublisher) {
    this.requestRepository = new RequestRepository(clients);
    this.eventPublisher = eventPublisher;
  }

  public CompletableFuture<Result<String>> anonymizeSingle(String requestId, String performedByUserId) {
    final UUID id;
    try {
      id = UUID.fromString(requestId);
    } catch (IllegalArgumentException e) {
      return CompletableFuture.completedFuture(
        failed(new ValidationErrorFailure(new ValidationError("invalidRequestId", "requestId", requestId)))
      );
    }

    return fetchRequest(id)
      .thenApply(flatMapResult(req -> validateStatus(req, requestId)))        // 404 / 422 mapping
      .thenApply(flatMapResult(this::scrubPii))                               // apply 2292 field clearing
      .thenCompose(r -> r.after(requestRepository::update))                   // persist
      .thenCompose(r -> r.after(updated -> publishLog(updated, performedByUserId))) // audit/event
      .thenApply(mapResult(updated -> requestId));                            // success payload = id
  }

  private CompletableFuture<Result<Request>> fetchRequest(UUID id) {
    return requestRepository.getById(id.toString())
      .thenApply(res -> res.failWhen(
        r -> succeeded(r == null),
        r -> new RecordNotFoundFailure("Request", id.toString())
      ));
  }

  private Result<Request> validateStatus(Request request, String id) {
    final RequestStatus status = request.getStatus();

    if (ALLOWED_STATUSES.contains(status)) {
      return succeeded(request);
    }
    if (OPEN_STATUSES.contains(status)) {
      return failed(new ValidationErrorFailure((new ValidationError("requestNotClosed", "requestId", id))));
    }
    return failed(new ValidationErrorFailure((new ValidationError("requestNotEligibleForAnonymization", "requestId", id))));
  }

  private Result<Request> scrubPii(Request req) {
    final JsonObject rep = req.asJson().copy();

    final boolean hadRequester = rep.containsKey("requester") || rep.containsKey("requesterId");
    final boolean hadProxy = rep.containsKey("proxy") || rep.containsKey("proxyUserId");
    final boolean isDelivery = req.getfulfillmentPreference() == RequestFulfillmentPreference.DELIVERY;
    final boolean hadDelivery = isDelivery && (rep.containsKey("deliveryAddress") || rep.containsKey("deliveryAddressTypeId"));

    if (!hadRequester && !hadProxy && (!isDelivery || !hadDelivery)) {
      return succeeded(req);
    }

    rep.remove("requesterId");
    rep.remove("proxyUserId");
    rep.remove("requester");
    rep.remove("proxy");

    if (req.getfulfillmentPreference() == RequestFulfillmentPreference.DELIVERY) {
      rep.remove("deliveryAddress");
      rep.remove("deliveryAddressTypeId");
    }

    return succeeded(Request.from(rep));
  }

  private CompletableFuture<Result<Request>> publishLog(Request req, String performedByUserId) {
    return eventPublisher.publishRequestAnonymizedLog(req)
      .thenApply(r -> r.map(v -> req));
  }
}
