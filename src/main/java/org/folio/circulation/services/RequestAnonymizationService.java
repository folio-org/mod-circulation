package org.folio.circulation.services;

import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestFulfillmentPreference;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.util.UuidUtil;
import io.vertx.core.json.JsonObject;

public class RequestAnonymizationService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
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
    ItemRepository itemRepository = new ItemRepository(clients);
    UserRepository userRepository = new UserRepository(clients);
    LoanRepository loanRepository = new LoanRepository(clients, itemRepository, userRepository);

    this.requestRepository = RequestRepository.using(clients, itemRepository, userRepository, loanRepository);

    this.eventPublisher = eventPublisher;
  }

  public RequestAnonymizationService(RequestRepository requestRepository,
      EventPublisher eventPublisher) {

    this.requestRepository = requestRepository;
    this.eventPublisher = eventPublisher;
  }

  public CompletableFuture<Result<String>> anonymizeSingle(String requestId) {
    log.debug("anonymizeSingle:: ");
    if (!UuidUtil.isUuid(requestId)) {
      return CompletableFuture.completedFuture(ValidationErrorFailure.failedValidation(
        "invalidRequestId", "requestId", requestId)
      );
    }

    return fetchRequest(requestId)
      .thenApply(flatMapResult(req -> validateStatus(req, requestId)))
      .thenApply(mapResult(this::scrubPii))
      .thenCompose(r -> r.after(requestRepository::update))
      .thenCompose(r -> r.after(this::publishLog))
      .thenApply(mapResult(updated -> requestId));
  }

  private CompletableFuture<Result<Request>> fetchRequest(String requestId) {
    log.debug("fetchRequest:: ");
    return requestRepository.getById(requestId);
  }

  private Result<Request> validateStatus(Request request, String id) {
    final RequestStatus status = request.getStatus();
    log.info("validateStatus:: parameters requestId: {}, status: {}", id, status);

    if (ALLOWED_STATUSES.contains(status)) {
      return succeeded(request);
    }
    if (OPEN_STATUSES.contains(status)) {
      log.info("validateStatus:: request {} is not closed, status: {}", id, status);
      return failed(new ValidationErrorFailure((new ValidationError("requestNotClosed", "requestId", id))));
    }
    log.info("validateStatus:: request {} is not eligible for anonymization, status: {}", id, status);
    return failed(new ValidationErrorFailure((new ValidationError("requestNotEligibleForAnonymization", "requestId", id))));
  }

  private Request scrubPii(Request req) {
    log.info("scrubPii:: parameters requestId: {}", req::getId);
    final JsonObject rep = req.asJson();

    final boolean hadRequester = rep.containsKey("requester") || rep.containsKey("requesterId");
    final boolean hadProxy = rep.containsKey("proxy") || rep.containsKey("proxyUserId");
    final boolean isDelivery = req.getfulfillmentPreference() == RequestFulfillmentPreference.DELIVERY;
    final boolean hadDelivery = isDelivery && (rep.containsKey("deliveryAddress") || rep.containsKey("deliveryAddressTypeId"));

    if (!hadRequester && !hadProxy && (!isDelivery || !hadDelivery)) {
      return req;
    }

    rep.putNull("requesterId");
    rep.putNull("proxyUserId");
    rep.remove("requester");
    rep.remove("proxy");

    if (isDelivery) {
      rep.remove("deliveryAddress");
      rep.remove("deliveryAddressTypeId");
    }

    return Request.from(rep);
  }

  private CompletableFuture<Result<Void>> publishLog(Request req) {
    return eventPublisher.publishRequestAnonymizedLog(req);
  }
}
