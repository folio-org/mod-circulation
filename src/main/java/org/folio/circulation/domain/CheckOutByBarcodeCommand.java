package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;

import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.PROXY_USER_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.USER_BARCODE;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import io.vertx.core.json.JsonObject;

import org.folio.circulation.domain.notice.schedule.DueDateScheduledNoticeService;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticesRepository;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.PatronNoticePolicyRepository;
import org.folio.circulation.domain.representations.CheckOutByBarcodeRequest;
import org.folio.circulation.domain.validation.AlreadyCheckedOutValidator;
import org.folio.circulation.domain.validation.ExistingOpenLoanValidator;
import org.folio.circulation.domain.validation.InactiveUserValidator;
import org.folio.circulation.domain.validation.ItemMissingValidator;
import org.folio.circulation.domain.validation.ItemNotFoundValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.RequestedByAnotherPatronValidator;
import org.folio.circulation.domain.validation.ServicePointOfCheckoutPresentValidator;
import org.folio.circulation.resources.CheckOutStrategy;
import org.folio.circulation.resources.LoanNoticeSender;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;

public class CheckOutByBarcodeCommand {

  private Clients clients;
  private CheckOutStrategy checkOutStrategy;

  private JsonObject jsonRequest;
  private CheckOutByBarcodeRequest request;


  public CheckOutByBarcodeCommand(CheckOutStrategy checkOutStrategy, Clients clients) {
    this.checkOutStrategy = checkOutStrategy;
    this.clients = clients;
  }

  public CompletableFuture<Result<Loan>> execute(Loan loan, JsonObject jsonRequest) {
    this.request = CheckOutByBarcodeRequest.from(jsonRequest);
    this.jsonRequest = jsonRequest;

    return prepare(succeeded(new LoanAndRelatedRecords(loan)))
            .thenCompose(this::validate)
            .thenCompose(this::doWork)
            .thenCompose(this::finalize)
            .thenApply(this::getResult);
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> prepare(Result<LoanAndRelatedRecords> loanAndRelatedRecords) {
    UserRepository userRepository = new UserRepository(clients);
    ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    ConfigurationRepository configurationRepository= new ConfigurationRepository(clients);

    return completedFuture(loanAndRelatedRecords)
      .thenCombineAsync(userRepository.getUserByBarcode(request.getUserBarcode()), this::addUser)
      .thenCombineAsync(userRepository.getProxyUserByBarcode(request.getProxyUserBarcode()), this::addProxyUser)
      .thenCombineAsync(itemRepository.fetchByBarcode(request.getItemBarcode()), this::addItem)
      .thenComposeAsync(r -> r.after(requestQueueRepository::get))
      .thenCompose(r -> r.combineAfter(configurationRepository::findTimeZoneConfiguration,
        LoanAndRelatedRecords::withTimeZone));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> validate(
      Result<LoanAndRelatedRecords> loanAndRelatedRecords) {
    ServicePointOfCheckoutPresentValidator servicePointOfCheckoutPresentValidator
      = new ServicePointOfCheckoutPresentValidator(message ->
      singleValidationError(message, SERVICE_POINT_ID, request.getServicePointId()));

    InactiveUserValidator inactiveUserValidator = InactiveUserValidator.forUser(request.getUserBarcode());
    InactiveUserValidator inactiveProxyUserValidator = InactiveUserValidator.forProxy(request.getProxyUserBarcode());

    RequestedByAnotherPatronValidator requestedByAnotherPatronValidator = new RequestedByAnotherPatronValidator(
      message -> singleValidationError(message, USER_BARCODE, request.getUserBarcode()));

    AlreadyCheckedOutValidator alreadyCheckedOutValidator = new AlreadyCheckedOutValidator(
      message -> singleValidationError(message, ITEM_BARCODE, request.getItemBarcode()));

    ItemNotFoundValidator itemNotFoundValidator = new ItemNotFoundValidator(
      () -> singleValidationError(String.format("No item with barcode %s could be found", request.getItemBarcode()),
        ITEM_BARCODE, request.getItemBarcode()));

    ItemMissingValidator itemMissingValidator = new ItemMissingValidator(
      message -> singleValidationError(message, ITEM_BARCODE, request.getItemBarcode()));

    LoanRepository loanRepository = new LoanRepository(clients);
    ExistingOpenLoanValidator openLoanValidator = new ExistingOpenLoanValidator(
      loanRepository, message -> singleValidationError(message, ITEM_BARCODE, request.getItemBarcode()));

    ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> singleValidationError(
      "Cannot check out item via proxy when relationship is invalid",
      PROXY_USER_BARCODE, request.getProxyUserBarcode()));

    return completedFuture(loanAndRelatedRecords)
      .thenApply(servicePointOfCheckoutPresentValidator::refuseCheckOutWhenServicePointIsNotPresent)
      .thenApply(inactiveUserValidator::refuseWhenUserIsInactive)
      .thenApply(inactiveProxyUserValidator::refuseWhenUserIsInactive)
      .thenApply(itemNotFoundValidator::refuseWhenItemNotFound)
      .thenApply(alreadyCheckedOutValidator::refuseWhenItemIsAlreadyCheckedOut)
      .thenApply(itemMissingValidator::refuseWhenItemIsMissing)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenComposeAsync(r -> r.after(openLoanValidator::refuseWhenHasOpenLoan))
      .thenApply(requestedByAnotherPatronValidator::refuseWhenRequestedByAnotherPatron);
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> doWork(
      Result<LoanAndRelatedRecords> loanAndRelatedRecords) {
    LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    UpdateRequestQueue requestQueueUpdate = UpdateRequestQueue.using(clients);
    UpdateItem updateItem = new UpdateItem(clients);
    LoanService loanService = new LoanService(clients);
    PatronGroupRepository patronGroupRepository = new PatronGroupRepository(clients);
    LoanRepository loanRepository = new LoanRepository(clients);

    return completedFuture(loanAndRelatedRecords)
      .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenComposeAsync(r -> r.after(relatedRecords -> checkOutStrategy.checkOut(relatedRecords, jsonRequest, clients)))
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onCheckOut))
      .thenComposeAsync(r -> r.after(updateItem::onCheckOut))
      .thenComposeAsync(r -> r.after(loanService::truncateLoanWhenItemRecalled))
      .thenComposeAsync(r -> r.after(patronGroupRepository::findPatronGroupForLoanAndRelatedRecords))
      .thenComposeAsync(r -> r.after(loanRepository::createLoan));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> finalize(
      Result<LoanAndRelatedRecords> loanAndRelatedRecords) {
    PatronNoticePolicyRepository patronNoticePolicyRepository = new PatronNoticePolicyRepository(clients);
    LoanNoticeSender loanNoticeSender = LoanNoticeSender.using(clients);

    ScheduledNoticesRepository scheduledNoticesRepository = ScheduledNoticesRepository.using(clients);
    DueDateScheduledNoticeService scheduledNoticeService =
      new DueDateScheduledNoticeService(scheduledNoticesRepository, patronNoticePolicyRepository);

    return completedFuture(loanAndRelatedRecords)
      .thenApply(r -> r.next(loanNoticeSender::sendCheckOutPatronNotice))
      .thenApply(r -> r.next(scheduledNoticeService::scheduleNoticesForLoanDueDate));
  }

  private Result<Loan> getResult(Result<LoanAndRelatedRecords> loanAndRelatedRecords) {
    return loanAndRelatedRecords.map(LoanAndRelatedRecords::getLoan);
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
