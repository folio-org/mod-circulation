package org.folio.circulation.resources;

import static org.folio.circulation.domain.validation.CommonFailures.noItemFoundForIdFailure;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.validation.UserNotFoundValidator;
import org.folio.circulation.storage.ItemByIdInStorageFinder;
import org.folio.circulation.storage.SingleOpenLoanForItemInStorageFinder;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;

public class RenewByIdResource extends RenewalResource {
  public RenewByIdResource(String rootPath, RenewalStrategy renewalStrategy, HttpClient client) {
    super(rootPath, renewalStrategy, client);
  }

  @Override
  protected CompletableFuture<Result<Loan>> findLoan(
    JsonObject request,
    LoanRepository loanRepository,
    ItemRepository itemRepository,
    UserRepository userRepository) {

    final Result<RenewByIdRequest> requestResult
      = RenewByIdRequest.from(request);

    final String itemId = requestResult
      .map(RenewByIdRequest::getItemId)
      .orElse("unknown item ID");

    final SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder
      = new SingleOpenLoanForItemInStorageFinder(loanRepository, userRepository, false);

    final ItemByIdInStorageFinder itemFinder = new ItemByIdInStorageFinder(
      itemRepository, noItemFoundForIdFailure(itemId));

    return requestResult
      .after(checkInRequest -> itemFinder.findItemById(itemId))
      .thenComposeAsync(itemResult -> itemResult.after(singleOpenLoanFinder::findSingleOpenLoan))
      .thenApply(UserNotFoundValidator::refuseWhenUserNotFound)
      .thenApply(loanResult -> loanResult.combineToResult(requestResult,
        this::refuseWhenUserDoesNotMatch));
  }

  private Result<Loan> refuseWhenUserDoesNotMatch(
    Loan loan,
    RenewByIdRequest idRequest) {

    if(userMatches(loan, idRequest.getUserId())) {
      return succeeded(loan);
    }
    else {
      return failedValidation("Cannot renew item checked out to different user",
        RenewByIdRequest.USER_ID, idRequest.getUserId());
    }
  }

  private boolean userMatches(Loan loan, String expectedUserId) {
    return StringUtils.equals(loan.getUser().getId(), expectedUserId);
  }
}
