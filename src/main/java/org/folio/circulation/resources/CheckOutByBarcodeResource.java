package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.notice.NoticeContextUtil.createNoticeContextFromLoan;
import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.applyCLDDMForLoanAndRelatedRecords;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.PROXY_USER_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.USER_BARCODE;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.UUID;

import org.folio.circulation.domain.ConfigurationRepository;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.PatronNoticePolicyRepository;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.domain.representations.CheckOutByBarcodeRequest;
import org.folio.circulation.domain.representations.LoanProperties;
import org.folio.circulation.domain.validation.AlreadyCheckedOutValidator;
import org.folio.circulation.domain.validation.AwaitingPickupValidator;
import org.folio.circulation.domain.validation.ExistingOpenLoanValidator;
import org.folio.circulation.domain.validation.InactiveUserValidator;
import org.folio.circulation.domain.validation.ItemIsNotLoanableValidator;
import org.folio.circulation.domain.validation.ItemMissingValidator;
import org.folio.circulation.domain.validation.ItemNotFoundValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointOfCheckoutPresentValidator;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CreatedJsonResponseResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

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

    final JsonObject loanJson = new JsonObject();
    loanJson.put("id", UUID.randomUUID().toString());

    copyOrDefaultLoanDate(request, loanJson);

    final String itemBarcode = request.getString(CheckOutByBarcodeRequest.ITEM_BARCODE);
    final String userBarcode = request.getString(USER_BARCODE);
    final String proxyUserBarcode = request.getString(PROXY_USER_BARCODE);
    final String checkoutServicePointId = request.getString(SERVICE_POINT_ID);

    loanJson.put(LoanProperties.CHECKOUT_SERVICE_POINT_ID, checkoutServicePointId);
    Loan loan = Loan.from(loanJson);

    final Clients clients = Clients.create(context, client);

    final UserRepository userRepository = new UserRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final ClosedLibraryStrategyService strategyService = ClosedLibraryStrategyService.using(clients, loan.getLoanDate(), false);
    final PatronNoticePolicyRepository patronNoticePolicyRepository = new PatronNoticePolicyRepository(clients);
    final PatronNoticeService patronNoticeService = new PatronNoticeService(patronNoticePolicyRepository, clients);
    final ConfigurationRepository configurationRepository = new ConfigurationRepository(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> singleValidationError(
      "Cannot check out item via proxy when relationship is invalid",
      PROXY_USER_BARCODE, proxyUserBarcode));

    final ServicePointOfCheckoutPresentValidator servicePointOfCheckoutPresentValidator
      = new ServicePointOfCheckoutPresentValidator(message ->
      singleValidationError(message, SERVICE_POINT_ID, checkoutServicePointId));

    final AwaitingPickupValidator awaitingPickupValidator = new AwaitingPickupValidator(
      message -> singleValidationError(message, USER_BARCODE, userBarcode));

    final AlreadyCheckedOutValidator alreadyCheckedOutValidator = new AlreadyCheckedOutValidator(
      message -> singleValidationError(message, ITEM_BARCODE, itemBarcode));

    final ItemNotFoundValidator itemNotFoundValidator = new ItemNotFoundValidator(
      () -> singleValidationError(String.format("No item with barcode %s could be found", itemBarcode),
        ITEM_BARCODE, itemBarcode));

    final ItemMissingValidator itemMissingValidator = new ItemMissingValidator(
      message -> singleValidationError(message, ITEM_BARCODE, itemBarcode));

    final InactiveUserValidator inactiveUserValidator = InactiveUserValidator.forUser(userBarcode);
    final InactiveUserValidator inactiveProxyUserValidator = InactiveUserValidator.forProxy(proxyUserBarcode);

    final ExistingOpenLoanValidator openLoanValidator = new ExistingOpenLoanValidator(
      loanRepository, message -> singleValidationError(message, ITEM_BARCODE, itemBarcode));

    final ItemIsNotLoanableValidator itemIsNotLoanableValidator = new ItemIsNotLoanableValidator(
      () -> singleValidationError("Item is not loanable", ITEM_BARCODE, itemBarcode));

    final UpdateItem updateItem = new UpdateItem(clients);
    final UpdateRequestQueue requestQueueUpdate = UpdateRequestQueue.using(clients);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    completedFuture(succeeded(new LoanAndRelatedRecords(loan)))
      .thenApply(servicePointOfCheckoutPresentValidator::refuseCheckOutWhenServicePointIsNotPresent)
      .thenCombineAsync(userRepository.getUserByBarcode(userBarcode), this::addUser)
      .thenCombineAsync(userRepository.getProxyUserByBarcode(proxyUserBarcode), this::addProxyUser)
      .thenApply(inactiveUserValidator::refuseWhenUserIsInactive)
      .thenApply(inactiveProxyUserValidator::refuseWhenUserIsInactive)
      .thenCombineAsync(itemRepository.fetchByBarcode(itemBarcode), this::addItem)
      .thenApply(itemNotFoundValidator::refuseWhenItemNotFound)
      .thenApply(alreadyCheckedOutValidator::refuseWhenItemIsAlreadyCheckedOut)
      .thenApply(itemMissingValidator::refuseWhenItemIsMissing)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenComposeAsync(r -> r.after(openLoanValidator::refuseWhenHasOpenLoan))
      .thenComposeAsync(r -> r.after(requestQueueRepository::get))
      .thenApply(awaitingPickupValidator::refuseWhenUserIsNotAwaitingPickup)
      .thenComposeAsync(r -> r.after(configurationRepository::lookupTimeZone))
      .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenApply(itemIsNotLoanableValidator::refuseWhenItemIsNotLoanable)
      .thenApply(r -> r.next(this::calculateDefaultInitialDueDate))
      .thenComposeAsync(r -> r.after(records -> applyCLDDMForLoanAndRelatedRecords(strategyService, records)))
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onCheckOut))
      .thenComposeAsync(r -> r.after(updateItem::onCheckOut))
      .thenComposeAsync(r -> r.after(loanRepository::createLoan))
      .thenApply(r -> r.next(records -> sendCheckOutPatronNotice(records, patronNoticeService)))
      .thenApply(r -> r.map(LoanAndRelatedRecords::getLoan))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(this::createdLoanFrom)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private Result<LoanAndRelatedRecords> calculateDefaultInitialDueDate(LoanAndRelatedRecords loanAndRelatedRecords) {
    Loan loan = loanAndRelatedRecords.getLoan();
    LoanPolicy loanPolicy = loanAndRelatedRecords.getLoanPolicy();
    return loanPolicy.calculateInitialDueDate(loan)
      .map(dueDate -> {
        loanAndRelatedRecords.getLoan().changeDueDate(dueDate);
        return loanAndRelatedRecords;
      });
  }

  private void copyOrDefaultLoanDate(JsonObject request, JsonObject loan) {
    final String loanDateProperty = "loanDate";

    if (request.containsKey(loanDateProperty)) {
      loan.put(loanDateProperty, request.getString(loanDateProperty));
    } else {
      loan.put(loanDateProperty, DateTime.now().toDateTime(DateTimeZone.UTC)
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

  private Result<LoanAndRelatedRecords> sendCheckOutPatronNotice(
    LoanAndRelatedRecords relatedRecords,
    PatronNoticeService patronNoticeService) {

    JsonObject noticeContext =
      createNoticeContextFromLoan(relatedRecords.getLoan(), relatedRecords.getTimeZone());

    PatronNoticeEvent noticeEvent = new PatronNoticeEventBuilder()
      .withItem(relatedRecords.getLoan().getItem())
      .withUser(relatedRecords.getLoan().getUser())
      .withEventType(NoticeEventType.CHECK_OUT)
      .withTiming(NoticeTiming.UPON_AT)
      .withNoticeContext(noticeContext)
      .build();
    patronNoticeService.acceptNoticeEvent(noticeEvent);
    return succeeded(relatedRecords);
  }

}
