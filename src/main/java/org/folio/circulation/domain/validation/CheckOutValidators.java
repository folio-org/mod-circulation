package org.folio.circulation.domain.validation;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.override.OverridableBlockType.ITEM_LIMIT_BLOCK;
import static org.folio.circulation.domain.override.OverridableBlockType.ITEM_NOT_LOANABLE_BLOCK;
import static org.folio.circulation.domain.override.OverridableBlockType.PATRON_BLOCK;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.PROXY_USER_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.USER_BARCODE;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_ITEM;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_PROXY_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_PROXY_RELATIONSHIP;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_ALREADY_CHECKED_OUT;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_HAS_OPEN_LOANS;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_IS_NOT_ALLOWED_FOR_CHECK_OUT;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_IS_NOT_LOANABLE;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_LIMIT_IS_REACHED;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_REQUESTED_BY_ANOTHER_PATRON;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.PROXY_USER_IS_INACTIVE;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.SERVICE_POINT_IS_NOT_PRESENT;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_AUTOMATICALLY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_MANUALLY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_INACTIVE;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.ErrorCode.ITEM_HAS_OPEN_LOAN;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.representations.CheckOutByBarcodeRequest;
import org.folio.circulation.domain.validation.overriding.BlockValidator;
import org.folio.circulation.domain.validation.overriding.OverridingLoanValidator;
import org.folio.circulation.infrastructure.storage.AutomatedPatronBlocksRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.services.RequestQueueService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.OkapiPermissions;
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
  private final BlockValidator<LoanAndRelatedRecords> itemLimitValidator;
  private final BlockValidator<LoanAndRelatedRecords> loanPolicyValidator;
  private final BlockValidator<LoanAndRelatedRecords> automatedPatronBlocksValidator;
  private final BlockValidator<LoanAndRelatedRecords> manualPatronBlocksValidator;

  private final CirculationErrorHandler errorHandler;

  public CheckOutValidators(CheckOutByBarcodeRequest request, Clients clients,
    CirculationErrorHandler errorHandler, OkapiPermissions permissions,
    LoanRepository loanRepository) {

    this.errorHandler = errorHandler;

    final AutomatedPatronBlocksRepository automatedPatronBlocksRepository =
      new AutomatedPatronBlocksRepository(clients);

    proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> singleValidationError(
      "Cannot check out item via proxy when relationship is invalid",
      PROXY_USER_BARCODE, request.getProxyUserBarcode()));

    servicePointOfCheckoutPresentValidator = new ServicePointOfCheckoutPresentValidator(message ->
      singleValidationError(message, SERVICE_POINT_ID, request.getCheckoutServicePointId()));

    requestedByAnotherPatronValidator = new RequestedByAnotherPatronValidator(
      message -> singleValidationError(message, USER_BARCODE, request.getUserBarcode()),
      RequestQueueService.using(clients));

    alreadyCheckedOutValidator = new AlreadyCheckedOutValidator(
      message -> singleValidationError(message, ITEM_BARCODE, request.getItemBarcode()));

    itemNotFoundValidator = new ItemNotFoundValidator(
      () -> singleValidationError(String.format("No item with barcode %s could be found",
        request.getItemBarcode()), ITEM_BARCODE, request.getItemBarcode()));

    itemStatusValidator = new ItemStatusValidator(this::errorWhenInIncorrectStatus);

    inactiveUserValidator = InactiveUserValidator.forUser(request.getUserBarcode());
    inactiveProxyUserValidator = InactiveUserValidator.forProxy(request.getProxyUserBarcode());

    openLoanValidator = new ExistingOpenLoanValidator(loanRepository,
      message -> singleValidationError(message, ITEM_BARCODE, request.getItemBarcode(),
        ITEM_HAS_OPEN_LOAN));

    itemLimitValidator = createItemLimitValidator(request, permissions,
      loanRepository);

    automatedPatronBlocksValidator = createAutomatedPatronBlocksValidator(request, permissions,
      automatedPatronBlocksRepository);

    loanPolicyValidator = createLoanPolicyValidator(request, permissions);

    manualPatronBlocksValidator = createManualPatronBlocksValidator(request, permissions, clients);
  }

  private ValidationErrorFailure errorWhenInIncorrectStatus(Item item) {
    String message =
      String.format("%s (%s) (Barcode: %s) has the item status %s and cannot be checked out",
        item.getTitle(),
        item.getMaterialTypeName(),
        item.getBarcode(),
        item.getStatusName());

    return singleValidationError(message, ITEM_BARCODE, item.getBarcode());
  }

  public Result<LoanAndRelatedRecords> refuseCheckOutWhenServicePointIsNotPresent(
    Result<LoanAndRelatedRecords> result) {

    return servicePointOfCheckoutPresentValidator.refuseCheckOutWhenServicePointIsNotPresent(result)
      .mapFailure(failure -> errorHandler.handleValidationError(failure,
        SERVICE_POINT_IS_NOT_PRESENT, result));
  }

  public Result<LoanAndRelatedRecords> refuseWhenUserIsInactive(
    Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasAny(FAILED_TO_FETCH_USER)) {
      return result;
    }

    return inactiveUserValidator.refuseWhenUserIsInactive(result)
      .mapFailure(failure -> errorHandler.handleValidationError(failure,
        USER_IS_INACTIVE, result));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>>
  refuseWhenCheckOutActionIsBlockedAutomaticallyForPatron(Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasAny(FAILED_TO_FETCH_USER)) {
      return completedFuture(result);
    }

    return result.after(l -> automatedPatronBlocksValidator.validate(l)
      .thenApply(r -> errorHandler.handleValidationResult(r, automatedPatronBlocksValidator.getErrorType(), result)));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>>
  refuseWhenCheckOutActionIsBlockedManuallyForPatron(Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasAny(FAILED_TO_FETCH_USER)) {
      return completedFuture(result);
    }

    return result.after(l -> manualPatronBlocksValidator.validate(l)
      .thenApply(r -> errorHandler.handleValidationResult(
        r, manualPatronBlocksValidator.getErrorType(), result)));
  }

  public Result<LoanAndRelatedRecords> refuseWhenProxyUserIsInactive(
    Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasAny(FAILED_TO_FETCH_PROXY_USER)) {
      return result;
    }

    return inactiveProxyUserValidator.refuseWhenUserIsInactive(result)
      .mapFailure(failure -> errorHandler.handleValidationError(failure,
        PROXY_USER_IS_INACTIVE, result));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenInvalidProxyRelationship(
    Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasAny(FAILED_TO_FETCH_USER, FAILED_TO_FETCH_PROXY_USER)) {
      return completedFuture(result);
    }

    return result.after(l -> proxyRelationshipValidator.refuseWhenInvalid(l)
      .thenApply(r -> errorHandler.handleValidationResult(r, INVALID_PROXY_RELATIONSHIP, l)));
  }

  public Result<LoanAndRelatedRecords> refuseWhenItemNotFound(
    Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasAny(FAILED_TO_FETCH_ITEM)) {
      return result;
    }

    return itemNotFoundValidator.refuseWhenItemNotFound(result)
      .mapFailure(failure -> errorHandler.handleValidationError(failure,
        FAILED_TO_FETCH_ITEM, result));
  }

  public Result<LoanAndRelatedRecords> refuseWhenItemIsAlreadyCheckedOut(
    Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasAny(FAILED_TO_FETCH_ITEM)) {
      return result;
    }

    return alreadyCheckedOutValidator.refuseWhenItemIsAlreadyCheckedOut(result)
      .mapFailure(failure -> errorHandler.handleValidationError(failure,
        ITEM_ALREADY_CHECKED_OUT, result));
  }

  public Result<LoanAndRelatedRecords> refuseWhenItemIsNotAllowedForCheckOut(
    Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasAny(FAILED_TO_FETCH_ITEM)) {
      return result;
    }

    return itemStatusValidator.refuseWhenItemIsNotAllowedForCheckOut(result)
      .mapFailure(failure -> errorHandler.handleValidationError(failure,
        ITEM_IS_NOT_ALLOWED_FOR_CHECK_OUT, result));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenItemHasOpenLoans(
    Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasAny(FAILED_TO_FETCH_ITEM)) {
      return completedFuture(result);
    }

    return result.after(l -> openLoanValidator.refuseWhenHasOpenLoan(l)
      .thenApply(r -> errorHandler.handleValidationResult(r, ITEM_HAS_OPEN_LOANS, l)));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenRequestedByAnotherPatron(
    Result<LoanAndRelatedRecords> result) {

    if (errorHandler.hasAny(FAILED_TO_FETCH_USER)) {
      return completedFuture(result);
    }

    return requestedByAnotherPatronValidator.refuseWhenRequestedByAnotherPatron(result)
      .thenApply(r -> r.mapFailure(failure -> errorHandler.handleValidationError(failure,
        ITEM_REQUESTED_BY_ANOTHER_PATRON, result)));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenItemLimitIsReached(
    Result<LoanAndRelatedRecords> result) {

    if (isLoanPolicyNotInitialized(result)) {
      return completedFuture(result);
    }

    return result.after(relatedRecords -> itemLimitValidator.validate(relatedRecords)
      .thenApply(r -> errorHandler.handleValidationResult(r, itemLimitValidator.getErrorType(), relatedRecords)));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenItemIsNotLoanable(
    Result<LoanAndRelatedRecords> result) {

    if (isLoanPolicyNotInitialized(result)) {
      return completedFuture(result);
    }

    return result.after(relatedRecords -> loanPolicyValidator.validate(relatedRecords)
      .thenApply(r -> errorHandler.handleValidationResult(r, loanPolicyValidator.getErrorType(), relatedRecords)));
  }

  private BlockValidator<LoanAndRelatedRecords> createAutomatedPatronBlocksValidator(CheckOutByBarcodeRequest request,
    OkapiPermissions permissions, AutomatedPatronBlocksRepository automatedPatronBlocksRepository) {

    return request.getBlockOverrides().getPatronBlockOverride().isRequested()
      ? new OverridingLoanValidator(PATRON_BLOCK, request.getBlockOverrides(), permissions)
      : new BlockValidator<>(USER_IS_BLOCKED_AUTOMATICALLY,
      new AutomatedPatronBlocksValidator(
        automatedPatronBlocksRepository)::refuseWhenCheckOutActionIsBlockedForPatron);
  }

  private BlockValidator<LoanAndRelatedRecords> createItemLimitValidator(CheckOutByBarcodeRequest request,
    OkapiPermissions permissions, LoanRepository loanRepository) {

    return request.getBlockOverrides().getItemLimitBlockOverride().isRequested()
      ? new OverridingLoanValidator(ITEM_LIMIT_BLOCK, request.getBlockOverrides(), permissions)
      : new BlockValidator<>(ITEM_LIMIT_IS_REACHED,
      new ItemLimitValidator(request, loanRepository)::refuseWhenItemLimitIsReached);
  }

  private BlockValidator<LoanAndRelatedRecords> createLoanPolicyValidator(CheckOutByBarcodeRequest request,
    OkapiPermissions permissions) {

    return request.getBlockOverrides().getItemNotLoanableBlockOverride().isRequested()
      ? new OverridingLoanValidator(ITEM_NOT_LOANABLE_BLOCK, request.getBlockOverrides(), permissions)
      : new BlockValidator<>(ITEM_IS_NOT_LOANABLE,
      new LoanPolicyValidator(request)::refuseWhenItemIsNotLoanable);
  }

  private BlockValidator<LoanAndRelatedRecords> createManualPatronBlocksValidator(
    CheckOutByBarcodeRequest request, OkapiPermissions permissions, Clients clients) {

    return request.getBlockOverrides().getPatronBlockOverride().isRequested()
      ? new OverridingLoanValidator(PATRON_BLOCK, request.getBlockOverrides(), permissions)
      : new BlockValidator<>(USER_IS_BLOCKED_MANUALLY,
      new UserManualBlocksValidator(clients)::refuseWhenUserIsBlocked);
  }

  private boolean isLoanPolicyNotInitialized(Result<LoanAndRelatedRecords> result) {
    return Optional.ofNullable(result.value())
      .map(LoanAndRelatedRecords::getLoan)
      .map(Loan::getLoanPolicy)
      .map(LoanPolicy::getId)
      .isEmpty();
  }
}
