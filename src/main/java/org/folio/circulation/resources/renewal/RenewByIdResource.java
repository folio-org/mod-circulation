package org.folio.circulation.resources.renewal;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.validation.CommonFailures.noItemFoundForIdFailure;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FIND_SINGLE_OPEN_LOAN;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_DOES_NOT_EXIST;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_DOES_NOT_MATCH;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.validation.UserNotFoundValidator;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.storage.ItemByIdInStorageFinder;
import org.folio.circulation.storage.SingleOpenLoanForItemInStorageFinder;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;

public class RenewByIdResource extends RenewalResource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public RenewByIdResource(HttpClient client) {
    super("/circulation/renew-by-id", client);
  }

  @Override
  protected CompletableFuture<Result<Loan>> findLoan(JsonObject request,
    LoanRepository loanRepository, ItemRepository itemRepository, UserRepository userRepository,
    CirculationErrorHandler errorHandler) {

    final Result<RenewByIdRequest> requestResult
      = RenewByIdRequest.from(request);

    final String itemId = requestResult
      .map(RenewByIdRequest::getItemId)
      .orElse("unknown item ID");

    log.debug("findLoan:: itemId={}", itemId);

    final SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder
      = new SingleOpenLoanForItemInStorageFinder(loanRepository, userRepository, false);

    final ItemByIdInStorageFinder itemFinder = new ItemByIdInStorageFinder(
      itemRepository, noItemFoundForIdFailure(itemId));

    return completedFuture(requestResult)
      .thenCompose(r -> lookupItem(itemFinder, itemId, errorHandler))
      .thenCompose(r -> r.after(item -> lookupLoan(singleOpenLoanFinder, item, errorHandler)))
      .thenApply(r -> r.next(loan -> refuseWhenUserNotFound(loan, errorHandler)))
      .thenApply(r -> r.next(loan -> refuseWhenUserDoesNotMatch(loan, requestResult.value(),
        errorHandler)));
  }

  private CompletableFuture<Result<Item>> lookupItem(ItemByIdInStorageFinder itemFinder,
    String itemId, CirculationErrorHandler errorHandler) {

    log.debug("lookupItem:: itemId={}", itemId);

    return itemFinder.findItemById(itemId)
      .thenApply(r -> errorHandler.handleValidationResult(r, ITEM_DOES_NOT_EXIST, (Item) null));
  }

  private CompletableFuture<Result<Loan>> lookupLoan(
    SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder, Item item,
    CirculationErrorHandler errorHandler) {

    if (errorHandler.hasAny(ITEM_DOES_NOT_EXIST)) {
      log.info("lookupLoan:: skipping because item does not exist");
      return completedFuture(succeeded(null));
    }

    log.debug("lookupLoan:: itemId={}", () -> item.getItemId());

    return singleOpenLoanFinder.findSingleOpenLoan(item)
      .thenApply(r -> errorHandler.handleValidationResult(r, FAILED_TO_FIND_SINGLE_OPEN_LOAN,
        (Loan) null));
  }

  private Result<Loan> refuseWhenUserNotFound(Loan loan,
    CirculationErrorHandler errorHandler) {

    if (errorHandler.hasAny(ITEM_DOES_NOT_EXIST, FAILED_TO_FIND_SINGLE_OPEN_LOAN)) {
      return succeeded(loan);
    }

    return UserNotFoundValidator.refuseWhenUserNotFound(succeeded(loan))
      .mapFailure(failure -> errorHandler.handleValidationError(failure,
        FAILED_TO_FETCH_USER, loan));
  }

  private Result<Loan> refuseWhenUserDoesNotMatch(Loan loan, RenewByIdRequest idRequest,
    CirculationErrorHandler errorHandler) {

    if (errorHandler.hasAny(ITEM_DOES_NOT_EXIST, FAILED_TO_FIND_SINGLE_OPEN_LOAN,
      FAILED_TO_FETCH_USER)) {

      return succeeded(loan);
    }

    if(userMatches(loan, idRequest.getUserId())) {
      return succeeded(loan);
    }
    else {
      log.warn("refuseWhenUserDoesNotMatch:: loan {} is checked out to different user", loan::getId);
      Result<Loan> result = failedValidation("Cannot renew item checked out to different user",
        RenewByIdRequest.USER_ID, idRequest.getUserId());

      return result.mapFailure(failure -> errorHandler.handleValidationError(failure,
          USER_DOES_NOT_MATCH, loan));
    }
  }

  private boolean userMatches(Loan loan, String expectedUserId) {
    return StringUtils.equals(loan.getUser().getId(), expectedUserId);
  }
}
