package org.folio.circulation.domain.validation;


import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.PROXY_USER_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.USER_BARCODE;
import static org.folio.circulation.domain.validation.overriding.OverridePermissions.OVERRIDE_ITEM_LIMIT_BLOCK;
import static org.folio.circulation.domain.validation.overriding.OverridePermissions.OVERRIDE_ITEM_NOT_LOANABLE_BLOCK;
import static org.folio.circulation.domain.validation.overriding.OverridePermissions.OVERRIDE_PATRON_BLOCK;
import static org.folio.circulation.resources.error.CirculationError.FAILED_TO_FETCH_ITEM;
import static org.folio.circulation.resources.error.CirculationError.FAILED_TO_FETCH_LOAN_POLICY;
import static org.folio.circulation.resources.error.CirculationError.FAILED_TO_FETCH_PROXY_USER;
import static org.folio.circulation.resources.error.CirculationError.FAILED_TO_FETCH_USER;
import static org.folio.circulation.resources.error.CirculationError.ITEM_ALREADY_CHECKED_OUT;
import static org.folio.circulation.resources.error.CirculationError.ITEM_HAS_OPEN_LOANS;
import static org.folio.circulation.resources.error.CirculationError.ITEM_IS_NOT_ALLOWED_FOR_CHECK_OUT;
import static org.folio.circulation.resources.error.CirculationError.ITEM_IS_NOT_LOANABLE;
import static org.folio.circulation.resources.error.CirculationError.ITEM_LIMIT_IS_REACHED;
import static org.folio.circulation.resources.error.CirculationError.ITEM_REQUESTED_BY_ANOTHER_PATRON;
import static org.folio.circulation.resources.error.CirculationError.PROXY_USER_EQUALS_TO_USER;
import static org.folio.circulation.resources.error.CirculationError.SERVICE_POINT_IS_NOT_PRESENT;
import static org.folio.circulation.resources.error.CirculationError.USER_IS_BLOCKED_AUTOMATICALLY;
import static org.folio.circulation.support.ValidationErrorFailure.singleLoanPolicyValidationError;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.representations.CheckOutByBarcodeRequest;
import org.folio.circulation.domain.validation.overriding.OverrideAutomatedPatronBlocksValidator;
import org.folio.circulation.domain.validation.overriding.OverrideItemLimitValidator;
import org.folio.circulation.domain.validation.overriding.OverrideLoanPolicyValidator;
import org.folio.circulation.domain.validation.overriding.OverrideValidation;
import org.folio.circulation.infrastructure.storage.AutomatedPatronBlocksRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.resources.CheckOutStrategy;
import org.folio.circulation.resources.OverrideCheckOutStrategy;
import org.folio.circulation.resources.error.CirculationErrorHandler;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

public class CheckOutValidators {
  private final ProxyRelationshipValidator proxyRelationshipValidator;
  private final ServicePointOfCheckoutPresentValidator servicePointOfCheckoutPresentValidator;
  private final RequestedByAnotherPatronValidator requestedByAnotherPatronValidator;
  private final AlreadyCheckedOutValidator alreadyCheckedOutValidator;
  private final ItemNotFoundValidator itemNotFoundValidator;
  private final ItemStatusValidator itemStatusValidator;
  private final InactiveUserValidator inactiveUserValidator;
  private final InactiveUserValidator inactiveProxyUserValidator;
  private final ExistingOpenLoanValidator openLoanValidator;
  private final OverrideValidation itemLimitValidator;
  private final OverrideValidation loanPolicyValidator;
  private final OverrideValidation automatedPatronBlocksValidator;

  private final CirculationErrorHandler errorHandler;

  public CheckOutValidators(CheckOutByBarcodeRequest request, Clients clients,
    CirculationErrorHandler errorHandler, Map<String, String> headers) {

    this.errorHandler = errorHandler;

    final LoanRepository loanRepository = new LoanRepository(clients);

    final AutomatedPatronBlocksRepository automatedPatronBlocksRepository =
      new AutomatedPatronBlocksRepository(clients);

    proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> singleValidationError(
      "Cannot check out item via proxy when relationship is invalid",
      PROXY_USER_BARCODE, request.getProxyUserBarcode()));

    servicePointOfCheckoutPresentValidator = new ServicePointOfCheckoutPresentValidator(message ->
    singleValidationError(message, SERVICE_POINT_ID, request.getCheckoutServicePointId()));

    requestedByAnotherPatronValidator = new RequestedByAnotherPatronValidator(
      message -> singleValidationError(message, USER_BARCODE, request.getUserBarcode()));

    alreadyCheckedOutValidator = new AlreadyCheckedOutValidator(
      message -> singleValidationError(message, ITEM_BARCODE, request.getItemBarcode()));

    itemNotFoundValidator = new ItemNotFoundValidator(
      () -> singleValidationError(String.format("No item with barcode %s could be found", request.getItemBarcode()),
        ITEM_BARCODE, request.getItemBarcode()));

    itemStatusValidator = new ItemStatusValidator(this::errorWhenInIncorrectStatus);

    inactiveUserValidator = InactiveUserValidator.forUser(request.getUserBarcode());
    inactiveProxyUserValidator = InactiveUserValidator.forProxy(request.getProxyUserBarcode());

    openLoanValidator = new ExistingOpenLoanValidator(
      loanRepository, message -> singleValidationError(message, ITEM_BARCODE, request.getItemBarcode()));

    itemLimitValidator = defineItemLimitValidator(request, headers, loanRepository);

    automatedPatronBlocksValidator = definePatronBlocksValidator(request, headers,
      automatedPatronBlocksRepository);

    loanPolicyValidator = defineLoanPolicyValidator(request, headers);
  }

  private ValidationErrorFailure errorWhenInIncorrectStatus(Item item) {
    String message =
      String.format("%s (%s) (Barcode:%s) has the item status %s and cannot be checked out",
        item.getTitle(),
        item.getMaterialTypeName(),
        item.getBarcode(),
        item.getStatusName());

    return singleValidationError(message, ITEM_BARCODE, item.getBarcode());
  }

  public Result<LoanAndRelatedRecords> refuseCheckOutWhenServicePointIsNotPresent(
    Result<LoanAndRelatedRecords> result) {

    return servicePointOfCheckoutPresentValidator.refuseCheckOutWhenServicePointIsNotPresent(result)
      .mapFailure(failure -> errorHandler.handleValidationError(failure, SERVICE_POINT_IS_NOT_PRESENT, result));
  }

  public Result<LoanAndRelatedRecords> refuseWhenUserIsInactive(
    Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasCirculationError(FAILED_TO_FETCH_USER)) {
      return result;
    }

    return inactiveUserValidator.refuseWhenUserIsInactive(result);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>>
  refuseWhenCheckOutActionIsBlockedForPatron(Result<LoanAndRelatedRecords> result) {

    if (result.failed() || errorHandler.hasCirculationError(FAILED_TO_FETCH_USER)) {
      return completedFuture(result);
    }

    return automatedPatronBlocksValidator.validate(result.value())
      .thenApply(r -> errorHandler.handleValidationResult(r, USER_IS_BLOCKED_AUTOMATICALLY, result));
  }

  public Result<LoanAndRelatedRecords> refuseWhenProxyUserIsInactive(
    Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasCirculationError(FAILED_TO_FETCH_PROXY_USER)) {
      return result;
    }

    return inactiveProxyUserValidator.refuseWhenUserIsInactive(result);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenProxyUserIsTheSameAsUser(
    Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasCirculationError(FAILED_TO_FETCH_USER, FAILED_TO_FETCH_PROXY_USER)) {
      return completedFuture(result);
    }

    return result.after(l -> proxyRelationshipValidator.refuseWhenInvalid(l)
      .thenApply(r -> errorHandler.handleValidationResult(r, PROXY_USER_EQUALS_TO_USER, l)));
  }

  public Result<LoanAndRelatedRecords> refuseWhenItemNotFound(
    Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasCirculationError(FAILED_TO_FETCH_ITEM)) {
      return result;
    }

    return itemNotFoundValidator.refuseWhenItemNotFound(result)
      .mapFailure(failure -> errorHandler.handleValidationError(failure, FAILED_TO_FETCH_ITEM, result));
  }

  public Result<LoanAndRelatedRecords> refuseWhenItemIsAlreadyCheckedOut(
    Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasCirculationError(FAILED_TO_FETCH_ITEM)) {
      return result;
    }

    return alreadyCheckedOutValidator.refuseWhenItemIsAlreadyCheckedOut(result)
      .mapFailure(failure -> errorHandler.handleValidationError(failure, ITEM_ALREADY_CHECKED_OUT, result));
  }

  public Result<LoanAndRelatedRecords> refuseWhenItemIsNotAllowedForCheckOut(
    Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasCirculationError(FAILED_TO_FETCH_ITEM)) {
      return result;
    }

    return itemStatusValidator.refuseWhenItemIsNotAllowedForCheckOut(result)
      .mapFailure(failure -> errorHandler.handleValidationError(failure, ITEM_IS_NOT_ALLOWED_FOR_CHECK_OUT, result));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenItemHasOpenLoans(
    Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasCirculationError(FAILED_TO_FETCH_ITEM)) {
      return completedFuture(result);
    }

    return result.after(l -> openLoanValidator.refuseWhenHasOpenLoan(l)
      .thenApply(r -> errorHandler.handleValidationResult(r, ITEM_HAS_OPEN_LOANS, l)));
  }

  public Result<LoanAndRelatedRecords> refuseWhenRequestedByAnotherPatron(
    Result<LoanAndRelatedRecords> result) {

    return requestedByAnotherPatronValidator.refuseWhenRequestedByAnotherPatron(result)
      .mapFailure(failure -> errorHandler.handleValidationError(failure, ITEM_REQUESTED_BY_ANOTHER_PATRON, result));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenItemLimitIsReached(
    Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasCirculationError(FAILED_TO_FETCH_ITEM, FAILED_TO_FETCH_LOAN_POLICY)) {
      return completedFuture(result);
    }

    return result.after(relatedRecords -> itemLimitValidator.validate(relatedRecords)
      .thenApply(r -> errorHandler.handleValidationResult(r, ITEM_LIMIT_IS_REACHED, relatedRecords)));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenItemIsNotLoanable(
    Result<LoanAndRelatedRecords> result, CheckOutStrategy checkOutStrategy) {

    if (errorHandler.hasCirculationError(FAILED_TO_FETCH_ITEM, FAILED_TO_FETCH_LOAN_POLICY)
      || checkOutStrategy instanceof OverrideCheckOutStrategy) {
      return completedFuture(result);
    }

    return result.after(relatedRecords -> loanPolicyValidator.validate(relatedRecords)
      .thenApply(r -> errorHandler.handleValidationResult(r, ITEM_IS_NOT_LOANABLE, relatedRecords)));
  }

  private OverrideValidation definePatronBlocksValidator(CheckOutByBarcodeRequest request,
    Map<String, String> headers, AutomatedPatronBlocksRepository automatedPatronBlocksRepository) {

    return request.isPatronBlockOverriding()
      ? new OverrideAutomatedPatronBlocksValidator(message -> singleValidationError(
      message, "patron-block", OVERRIDE_PATRON_BLOCK.getValue()), headers, request)
      : new AutomatedPatronBlocksValidator(automatedPatronBlocksRepository,
      messages -> new ValidationErrorFailure(messages.stream()
        .map(message -> new ValidationError(message, new HashMap<>()))
        .collect(Collectors.toList())));
  }

  private OverrideValidation defineItemLimitValidator(CheckOutByBarcodeRequest request,
    Map<String, String> headers, LoanRepository loanRepository) {

    return request.isItemLimitBlockOverriding()
      ? new OverrideItemLimitValidator(message -> singleValidationError(
      message, "item-limit-block", OVERRIDE_ITEM_LIMIT_BLOCK.getValue()), headers, request)
      : new ItemLimitValidator(message -> singleValidationError(
      message, ITEM_BARCODE, request.getItemBarcode()), loanRepository);
  }

  private OverrideValidation defineLoanPolicyValidator(CheckOutByBarcodeRequest request,
    Map<String, String> headers) {

    return request.isItemNotLoanableBlock()
      ? new OverrideLoanPolicyValidator(message -> singleValidationError(
      message, "item-not-loanable-block", OVERRIDE_ITEM_NOT_LOANABLE_BLOCK.getValue()),
      headers, request)
      : new LoanPolicyValidator(loanPolicy -> singleLoanPolicyValidationError(
      loanPolicy, "Item is not loanable", ITEM_BARCODE, request.getItemBarcode()));
  }
}
