package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.domain.representations.RequestProperties.ITEM_ID;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
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
  private final ServicePointPickupLocationValidator servicePointPickupLocationValidator;


  RequestFromRepresentationService(ItemRepository itemRepository,
    RequestQueueRepository requestQueueRepository, UserRepository userRepository,
    LoanRepository loanRepository, ServicePointRepository servicePointRepository,
    ProxyRelationshipValidator proxyRelationshipValidator,
    ServicePointPickupLocationValidator servicePointPickupLocationValidator) {

    this.loanRepository = loanRepository;
    this.itemRepository = itemRepository;
    this.requestQueueRepository = requestQueueRepository;
    this.userRepository = userRepository;
    this.servicePointRepository = servicePointRepository;
    this.proxyRelationshipValidator = proxyRelationshipValidator;
    this.servicePointPickupLocationValidator = servicePointPickupLocationValidator;
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> getRequestFrom(JsonObject representation) {
    return completedFuture(succeeded(representation))
      .thenApply(r -> r.next(this::validateStatus))
      .thenApply(r -> r.next(this::refuseWhenNoItemId))
      .thenApply(r -> r.map(this::removeRelatedRecordInformation))
      .thenApply(r -> r.map(Request::from))
      .thenComposeAsync(r -> r.combineAfter(itemRepository::fetchFor, Request::withItem))
      .thenComposeAsync(r -> r.combineAfter(userRepository::getUser, Request::withRequester))
      .thenComposeAsync(r -> r.combineAfter(userRepository::getProxyUser, Request::withProxy))
      .thenComposeAsync(r -> r.combineAfter(servicePointRepository::getServicePointForRequest, Request::withPickupServicePoint))
      .thenComposeAsync(r -> r.combineAfter(loanRepository::findOpenLoanForRequest, Request::withLoan))
      .thenComposeAsync(r -> r.combineAfter(this::getUserForExistingLoan, this::addUserToLoan))
      .thenApply(r -> r.map(RequestAndRelatedRecords::new))
      .thenComposeAsync(r -> r.combineAfter(requestQueueRepository::get,
        RequestAndRelatedRecords::withRequestQueue))
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenApply(servicePointPickupLocationValidator::checkServicePointPickupLocation);
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

  private Result<JsonObject> refuseWhenNoItemId(JsonObject representation) {
    String itemId = getProperty(representation, ITEM_ID);

    if (isBlank(itemId)) {
      return failedValidation("Cannot create a request with no item ID", "itemId", itemId);
    }
    else {
      return of(() -> representation);
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
