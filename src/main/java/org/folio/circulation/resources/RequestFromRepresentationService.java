package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointPickupLocationValidator;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;

import io.vertx.core.json.JsonObject;

class RequestFromRepresentationService {
  private final ItemRepository itemRepository;
  private final RequestQueueRepository requestQueueRepository;
  private final UserRepository userRepository;
  private final LoanRepository loanRepository;
  private final ServicePointRepository servicePointRepository;
  private final ProxyRelationshipValidator proxyRelationshipValidator;
  private final ServicePointPickupLocationValidator servicePointPickupLocationValidator;


  RequestFromRepresentationService(
    ItemRepository itemRepository,
    RequestQueueRepository requestQueueRepository,
    UserRepository userRepository,
    LoanRepository loanRepository,
    ServicePointRepository servicePointRepository,
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

  CompletableFuture<Result<RequestAndRelatedRecords>> getRequestFrom(
    JsonObject representation) {

    return completedFuture(succeeded(representation))
      .thenApply(r -> r.next(this::validateStatus))
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

    if(!status.isValid()) {
      //TODO: Replace this with validation error
      // (but don't want to change behaviour at the moment)
      return failed(new BadRequestFailure(RequestStatus.invalidStatusErrorMessage()));
    }
    else {
      status.writeTo(representation);
      return succeeded(representation);
    }
  }

  private JsonObject removeRelatedRecordInformation(JsonObject request) {
    request.remove("item");
    request.remove("requester");
    request.remove("proxy");
    request.remove("loan");
    request.remove("pickupServicePoint");
    request.remove("deliveryAddress");

    return request;
  }
}
