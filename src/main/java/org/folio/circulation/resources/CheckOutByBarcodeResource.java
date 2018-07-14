package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.*;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.representations.CheckOutByBarcodeRequest;
import org.folio.circulation.domain.validation.*;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import java.util.UUID;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.PROXY_USER_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.USER_BARCODE;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

public class CheckOutByBarcodeResource extends Resource {
  public CheckOutByBarcodeResource(HttpClient client) {
    super(client);
  }

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/check-out-by-barcode", router);

    routeRegistration.create(this::checkOut);
  }

  private void checkOut(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject request = routingContext.getBodyAsJson();

    final JsonObject loan = new JsonObject();
    loan.put("id", UUID.randomUUID().toString());

    copyOrDefaultLoanDate(request, loan);

    final String itemBarcode = request.getString("itemBarcode");
    final String userBarcode = request.getString("userBarcode");
    final String proxyUserBarcode = request.getString("proxyUserBarcode");

    final Clients clients = Clients.create(context, client);

    final UserRepository userRepository = new UserRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true);
    final RequestQueueRepository requestQueueRepository = new RequestQueueRepository(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> failure(
      "Cannot check out item via proxy when relationship is invalid",
      CheckOutByBarcodeRequest.PROXY_USER_BARCODE,
      proxyUserBarcode));

    final AwaitingPickupValidator awaitingPickupValidator = new AwaitingPickupValidator(
      message -> failure(message,
        CheckOutByBarcodeRequest.USER_BARCODE, userBarcode));

    final AlreadyCheckedOutValidator alreadyCheckedOutValidator = new AlreadyCheckedOutValidator(
      message -> failure(message, ITEM_BARCODE, itemBarcode));

    final ItemNotFoundValidator itemNotFoundValidator = new ItemNotFoundValidator(
      () -> failure(String.format("No item with barcode %s could be found", itemBarcode),
        ITEM_BARCODE, itemBarcode));

    final InactiveUserValidator inactiveUserValidator = new InactiveUserValidator(
      records -> records.getLoan().getUser(),
      "Cannot check out to inactive user",
      "Cannot determine if user is active or not",
      message -> failure(message, USER_BARCODE, userBarcode));

    final InactiveUserValidator inactiveProxyUserValidator = new InactiveUserValidator(
      LoanAndRelatedRecords::getProxyingUser,
      "Cannot check out via inactive proxying user",
      "Cannot determine if proxying user is active or not",
      message -> failure(message, PROXY_USER_BARCODE, proxyUserBarcode));

    final ExistingOpenLoanValidator openLoanValidator = new ExistingOpenLoanValidator(
      loanRepository, message -> failure(message, ITEM_BARCODE, itemBarcode));

    final UpdateItem updateItem = new UpdateItem(clients);
    final UpdateRequestQueue requestQueueUpdate = new UpdateRequestQueue(clients);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    completedFuture(HttpResult.succeeded(new LoanAndRelatedRecords(Loan.from(loan))))
      .thenCombineAsync(userRepository.getUserByBarcode(userBarcode), this::addUser)
      .thenCombineAsync(userRepository.getProxyUserByBarcode(proxyUserBarcode), this::addProxyUser)
      .thenApply(inactiveUserValidator::refuseWhenUserIsInactive)
      .thenApply(inactiveProxyUserValidator::refuseWhenUserIsInactive)
      .thenCombineAsync(itemRepository.fetchByBarcode(itemBarcode), this::addInventoryRecords)
      .thenApply(itemNotFoundValidator::refuseWhenItemNotFound)
      .thenApply(r -> r.map(mapBarcodes()))
      .thenApply(alreadyCheckedOutValidator::refuseWhenItemIsAlreadyCheckedOut)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenComposeAsync(r -> r.after(openLoanValidator::refuseWhenHasOpenLoan))
      .thenComposeAsync(r -> r.after(requestQueueRepository::get))
      .thenApply(awaitingPickupValidator::refuseWhenUserIsNotAwaitingPickup)
      .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenApply(r -> r.next(this::calculateDueDate))
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onCheckOut))
      .thenComposeAsync(r -> r.after(updateItem::onCheckOut))
      .thenComposeAsync(r -> r.after(loanRepository::createLoan))
      .thenApply(r -> r.map(LoanAndRelatedRecords::getLoan))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(this::createdLoanFrom)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private HttpResult<LoanAndRelatedRecords> calculateDueDate(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    final Loan loan = loanAndRelatedRecords.getLoan();
    final LoanPolicy loanPolicy = loanAndRelatedRecords.getLoanPolicy();

    return loanPolicy.calculateInitialDueDate(loan)
      .map(dueDate -> {
        loanAndRelatedRecords.getLoan().changeDueDate(dueDate);

        return loanAndRelatedRecords;
      });
  }

  private void copyOrDefaultLoanDate(JsonObject request, JsonObject loan) {
    final String loanDateProperty = "loanDate";
    if(request.containsKey(loanDateProperty)) {
      loan.put(loanDateProperty, request.getString(loanDateProperty));
    } else {
      loan.put(loanDateProperty, DateTime.now().toDateTime(DateTimeZone.UTC)
        .toString(ISODateTimeFormat.dateTime()));
    }
  }

  private Function<LoanAndRelatedRecords, LoanAndRelatedRecords> mapBarcodes() {
    return loanAndRelatedRecords -> {
      final Loan loan = loanAndRelatedRecords.getLoan();

      if(loanAndRelatedRecords.getProxyingUser() != null) {
        loan.changeProxyUser(loanAndRelatedRecords.getProxyingUser().getId());
      }

      return loanAndRelatedRecords;
    };
  }

  private WritableHttpResult<JsonObject> createdLoanFrom(HttpResult<JsonObject> result) {
    if(result.failed()) {
      return HttpResult.failed(result.cause());
    }
    else {
      return new CreatedJsonHttpResult(result.value(),
        String.format("/circulation/loans/%s", result.value().getString("id")));
    }
  }

  private HttpResult<LoanAndRelatedRecords> addProxyUser(
    HttpResult<LoanAndRelatedRecords> loanResult,
    HttpResult<User> getUserResult) {

    return HttpResult.combine(loanResult, getUserResult,
      LoanAndRelatedRecords::withProxyingUser);
  }

  private HttpResult<LoanAndRelatedRecords> addUser(
    HttpResult<LoanAndRelatedRecords> loanResult,
    HttpResult<User> getUserResult) {

    return HttpResult.combine(loanResult, getUserResult,
      LoanAndRelatedRecords::withRequestingUser);
  }

  private HttpResult<LoanAndRelatedRecords> addInventoryRecords(
    HttpResult<LoanAndRelatedRecords> loanResult,
    HttpResult<Item> inventoryRecordsResult) {

    return HttpResult.combine(loanResult, inventoryRecordsResult,
      LoanAndRelatedRecords::withItem);
  }
}
