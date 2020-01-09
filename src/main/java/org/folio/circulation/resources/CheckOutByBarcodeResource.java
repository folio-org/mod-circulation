package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;

import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.PROXY_USER_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.USER_BARCODE;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.UUID;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.joda.time.format.ISODateTimeFormat;

import org.folio.circulation.domain.ConfigurationRepository;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.LoanService;
import org.folio.circulation.domain.PatronGroupRepository;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.notice.schedule.DueDateScheduledNoticeService;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticesRepository;
import org.folio.circulation.domain.notice.session.PatronActionSessionService;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.PatronNoticePolicyRepository;
import org.folio.circulation.domain.representations.LoanProperties;
import org.folio.circulation.domain.validation.AlreadyCheckedOutValidator;
import org.folio.circulation.domain.validation.ExistingOpenLoanValidator;
import org.folio.circulation.domain.validation.InactiveUserValidator;
import org.folio.circulation.domain.validation.ItemLimitValidator;
import org.folio.circulation.domain.validation.ItemNotFoundValidator;
import org.folio.circulation.domain.validation.ItemStatusValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.RequestedByAnotherPatronValidator;
import org.folio.circulation.domain.validation.ServicePointOfCheckoutPresentValidator;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.CreatedJsonResponseResult;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

public class CheckOutByBarcodeResource extends Resource {

  private final String rootPath;
  private final CheckOutStrategy checkOutStrategy;

  public CheckOutByBarcodeResource(String rootPath, HttpClient client, CheckOutStrategy checkOutStrategy) {
    super(client);
    this.rootPath = rootPath;
    this.checkOutStrategy = checkOutStrategy;
  }

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      rootPath, router);

    routeRegistration.create(this::checkOut);
  }

  private void checkOut(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject request = routingContext.getBodyAsJson();

    final JsonObject loanJson = new JsonObject();
    loanJson.put("id", UUID.randomUUID().toString());

    copyOrDefaultLoanDate(request, loanJson);

    final String itemBarcode = request.getString(ITEM_BARCODE);
    final String userBarcode = request.getString(USER_BARCODE);
    final String proxyUserBarcode = request.getString(PROXY_USER_BARCODE);
    final String checkoutServicePointId = request.getString(SERVICE_POINT_ID);

    loanJson.put(LoanProperties.CHECKOUT_SERVICE_POINT_ID, checkoutServicePointId);
    Loan loan = Loan.from(loanJson);

    final Clients clients = Clients.create(context, client);

    final UserRepository userRepository = new UserRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanService loanService = new LoanService(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final PatronNoticePolicyRepository patronNoticePolicyRepository = new PatronNoticePolicyRepository(clients);
    final PatronGroupRepository patronGroupRepository = new PatronGroupRepository(clients);
    final ConfigurationRepository configurationRepository = new ConfigurationRepository(clients);
    final ScheduledNoticesRepository scheduledNoticesRepository = ScheduledNoticesRepository.using(clients);
    final DueDateScheduledNoticeService scheduledNoticeService =
      new DueDateScheduledNoticeService(scheduledNoticesRepository, patronNoticePolicyRepository);

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> singleValidationError(
      "Cannot check out item via proxy when relationship is invalid",
      PROXY_USER_BARCODE, proxyUserBarcode));

    final ServicePointOfCheckoutPresentValidator servicePointOfCheckoutPresentValidator
      = new ServicePointOfCheckoutPresentValidator(message ->
      singleValidationError(message, SERVICE_POINT_ID, checkoutServicePointId));

    final RequestedByAnotherPatronValidator requestedByAnotherPatronValidator = new RequestedByAnotherPatronValidator(
      message -> singleValidationError(message, USER_BARCODE, userBarcode));

    final AlreadyCheckedOutValidator alreadyCheckedOutValidator = new AlreadyCheckedOutValidator(
      message -> singleValidationError(message, ITEM_BARCODE, itemBarcode));

    final ItemNotFoundValidator itemNotFoundValidator = new ItemNotFoundValidator(
      () -> singleValidationError(String.format("No item with barcode %s could be found", itemBarcode),
        ITEM_BARCODE, itemBarcode));

    final ItemStatusValidator itemStatusValidator = new ItemStatusValidator(
      message -> singleValidationError(message, ITEM_BARCODE, itemBarcode));

    final InactiveUserValidator inactiveUserValidator = InactiveUserValidator.forUser(userBarcode);
    final InactiveUserValidator inactiveProxyUserValidator = InactiveUserValidator.forProxy(proxyUserBarcode);

    final ExistingOpenLoanValidator openLoanValidator = new ExistingOpenLoanValidator(
      loanRepository, message -> singleValidationError(message, ITEM_BARCODE, itemBarcode));

    final ItemLimitValidator itemLimitValidator = new ItemLimitValidator(
      message -> singleValidationError(message, ITEM_BARCODE, itemBarcode), loanRepository);

    final UpdateItem updateItem = new UpdateItem(clients);
    final UpdateRequestQueue requestQueueUpdate = UpdateRequestQueue.using(clients);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    final PatronActionSessionService patronActionSessionService =
      PatronActionSessionService.using(clients);

    completedFuture(succeeded(new LoanAndRelatedRecords(loan)))
      .thenApply(servicePointOfCheckoutPresentValidator::refuseCheckOutWhenServicePointIsNotPresent)
      .thenCombineAsync(userRepository.getUserByBarcode(userBarcode), this::addUser)
      .thenCombineAsync(userRepository.getProxyUserByBarcode(proxyUserBarcode), this::addProxyUser)
      .thenApply(inactiveUserValidator::refuseWhenUserIsInactive)
      .thenApply(inactiveProxyUserValidator::refuseWhenUserIsInactive)
      .thenCombineAsync(itemRepository.fetchByBarcode(itemBarcode), this::addItem)
      .thenApply(itemNotFoundValidator::refuseWhenItemNotFound)
      .thenApply(alreadyCheckedOutValidator::refuseWhenItemIsAlreadyCheckedOut)
      .thenApply(itemStatusValidator::refuseWhenItemStatusIsInvalid)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenComposeAsync(r -> r.after(openLoanValidator::refuseWhenHasOpenLoan))
      .thenComposeAsync(r -> r.after(requestQueueRepository::get))
      .thenApply(requestedByAnotherPatronValidator::refuseWhenRequestedByAnotherPatron)
      .thenCompose(r -> r.combineAfter(configurationRepository::findTimeZoneConfiguration,
        LoanAndRelatedRecords::withTimeZone))
      .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenComposeAsync(r -> r.after(itemLimitValidator::refuseWhenItemLimitIsReached))
      .thenComposeAsync(r -> r.after(relatedRecords -> checkOutStrategy.checkOut(relatedRecords, request, clients)))
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onCheckOut))
      .thenComposeAsync(r -> r.after(updateItem::onCheckOut))
      .thenComposeAsync(r -> r.after(loanService::truncateLoanWhenItemRecalled))
      .thenComposeAsync(r -> r.after(patronGroupRepository::findPatronGroupForLoanAndRelatedRecords))
      .thenComposeAsync(r -> r.after(loanRepository::createLoan))
      .thenComposeAsync(r -> r.after(patronActionSessionService::saveCheckOutSessionRecord))
      .thenApply(r -> r.next(scheduledNoticeService::scheduleNoticesForLoanDueDate))
      .thenApply(r -> r.map(LoanAndRelatedRecords::getLoan))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(this::createdLoanFrom)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private void copyOrDefaultLoanDate(JsonObject request, JsonObject loan) {
    final String loanDateProperty = "loanDate";

    if (request.containsKey(loanDateProperty)) {
      loan.put(loanDateProperty, request.getString(loanDateProperty));
    } else {
      loan.put(loanDateProperty, ClockManager.getClockManager().getDateTime()
        .toString(ISODateTimeFormat.dateTime()));
    }
  }

  private ResponseWritableResult<JsonObject> createdLoanFrom(Result<JsonObject> result) {
    if (result.failed()) {
      return failed(result.cause());
    } else {
      return new CreatedJsonResponseResult(result.value(),
        String.format("/circulation/loans/%s", result.value().getString("id")));
    }
  }

  private Result<LoanAndRelatedRecords> addProxyUser(
    Result<LoanAndRelatedRecords> loanResult,
    Result<User> getUserResult) {

    return Result.combine(loanResult, getUserResult,
      LoanAndRelatedRecords::withProxyingUser);
  }

  private Result<LoanAndRelatedRecords> addUser(
    Result<LoanAndRelatedRecords> loanResult,
    Result<User> getUserResult) {

    return Result.combine(loanResult, getUserResult,
      LoanAndRelatedRecords::withRequestingUser);
  }

  private Result<LoanAndRelatedRecords> addItem(
    Result<LoanAndRelatedRecords> loanResult,
    Result<Item> inventoryRecordsResult) {

    return Result.combine(loanResult, inventoryRecordsResult,
      LoanAndRelatedRecords::withItem);
  }
}
