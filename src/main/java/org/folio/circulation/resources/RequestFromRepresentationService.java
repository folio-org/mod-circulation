package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_ITEM;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_LOAN;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_PROXY_RELATIONSHIP;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_PROXY_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_REQUEST_QUEUE;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_SERVICE_POINT;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_ITEM_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_PICKUP_SERVICE_POINT;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_PROXY_RELATIONSHIP;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_STATUS;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointPickupLocationValidator;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

class RequestFromRepresentationService {
  private final ItemRepository itemRepository;
  private final RequestQueueRepository requestQueueRepository;
  private final UserRepository userRepository;
  private final LoanRepository loanRepository;
  private final ServicePointRepository servicePointRepository;
  private final ProxyRelationshipValidator proxyRelationshipValidator;
  private final ServicePointPickupLocationValidator pickupLocationValidator;
  private final CirculationErrorHandler errorHandler;


  RequestFromRepresentationService(ItemRepository itemRepository,
    RequestQueueRepository requestQueueRepository, UserRepository userRepository,
    LoanRepository loanRepository, ServicePointRepository servicePointRepository,
    ProxyRelationshipValidator proxyRelationshipValidator,
    ServicePointPickupLocationValidator pickupLocationValidator,
    CirculationErrorHandler errorHandler) {

    this.loanRepository = loanRepository;
    this.itemRepository = itemRepository;
    this.requestQueueRepository = requestQueueRepository;
    this.userRepository = userRepository;
    this.servicePointRepository = servicePointRepository;
    this.proxyRelationshipValidator = proxyRelationshipValidator;
    this.pickupLocationValidator = pickupLocationValidator;
    this.errorHandler = errorHandler;
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> getRequestFrom(JsonObject representation) {
    return completedFuture(succeeded(representation))
      .thenApply(r -> r.next(this::validateStatus)
        .mapFailure(err -> errorHandler.handleError(err, INVALID_STATUS, r)))
      .thenApply(r -> r.map(this::removeRelatedRecordInformation))
      .thenApply(r -> r.map(Request::from))
      .thenComposeAsync(this::fetchItem)
      .thenComposeAsync(this::fetchLoan)
      .thenComposeAsync(r -> r.combineAfter(userRepository::getUser, Request::withRequester)
        .thenApply(res -> errorHandler.handleResult(res, FAILED_TO_FETCH_USER, r)))
      .thenComposeAsync(r -> r.combineAfter(userRepository::getProxyUser, Request::withProxy)
        .thenApply(res -> errorHandler.handleResult(res, FAILED_TO_FETCH_PROXY_USER, r)))
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid)
        .thenApply(res -> errorHandler.handleValidationResult(res, INVALID_PROXY_RELATIONSHIP, r))
        .thenApply(res -> errorHandler.handleResult(res, FAILED_TO_FETCH_PROXY_RELATIONSHIP, r)))
      .thenComposeAsync(r -> r.combineAfter(
        servicePointRepository::getServicePointForRequest, Request::withPickupServicePoint)
        .thenApply(res -> errorHandler.handleResult(res, FAILED_TO_FETCH_SERVICE_POINT, r)))
      .thenApply(r -> r.map(RequestAndRelatedRecords::new))
      .thenComposeAsync(r -> r.combineAfter(
        requestQueueRepository::get, RequestAndRelatedRecords::withRequestQueue)
        .thenApply(res -> errorHandler.handleResult(res, FAILED_TO_FETCH_REQUEST_QUEUE, r)))
      .thenApply(r -> r.next(pickupLocationValidator::refuseInvalidPickupServicePoint)
        .mapFailure(err -> errorHandler.handleValidationError(err, INVALID_PICKUP_SERVICE_POINT, r)));
  }

  private CompletableFuture<Result<Request>> fetchItem(Result<Request> result) {
    return result.next(this::refuseWhenNoItemId)
      .combineAfter(itemRepository::fetchFor, Request::withItem)
      .thenApply(r -> errorHandler.handleValidationResult(r, INVALID_ITEM_ID, result))
      .thenApply(r -> errorHandler.handleResult(r, FAILED_TO_FETCH_ITEM, result));
  }

  private CompletableFuture<Result<Request>> fetchLoan(Result<Request> result) {
    if (errorHandler.hasAny(INVALID_ITEM_ID, FAILED_TO_FETCH_ITEM)) {
      return completedFuture(result);
    }

    return result.combineAfter(loanRepository::findOpenLoanForRequest, Request::withLoan)
      .thenComposeAsync(r -> r.combineAfter(this::getUserForExistingLoan, this::addUserToLoan))
      .thenApply(r -> errorHandler.handleResult(r, FAILED_TO_FETCH_LOAN, result));
  }

  private CompletableFuture<Result<User>> getUserForExistingLoan(Request request) {
    Loan loan = request.getLoan();

    if (loan == null) {
      return ofAsync(() -> null);
    }

    return userRepository.getUser(loan.getUserId());
  }

  private Request addUserToLoan(Request request, User user) {
    if (request.getLoan() == null) {
      return request;
    }
    return request.withLoan(request.getLoan().withUser(user));
  }

  private Result<JsonObject> validateStatus(JsonObject representation) {
    RequestStatus status = RequestStatus.from(representation);

    if (!status.isValid()) {
      return failed(new BadRequestFailure(RequestStatus.invalidStatusErrorMessage()));
    }
    else {
      status.writeTo(representation);
      return succeeded(representation);
    }
  }

  private Result<Request> refuseWhenNoItemId(Request request) {
    final String itemId = request.getItemId();

    if (isBlank(itemId)) {
      return failedValidation("Cannot create a request with no item ID", "itemId",
        itemId);
    }
    else {
      return of(() -> request);
    }
  }

  private JsonObject removeRelatedRecordInformation(JsonObject representation) {
    representation.remove("item");
    representation.remove("requester");
    representation.remove("proxy");
    representation.remove("loan");
    representation.remove("pickupServicePoint");
    representation.remove("deliveryAddress");

    return representation;
  }
}
