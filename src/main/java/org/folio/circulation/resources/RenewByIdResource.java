package org.folio.circulation.resources;

import static org.folio.circulation.domain.validation.CommonFailures.moreThanOneOpenLoanFailure;
import static org.folio.circulation.domain.validation.CommonFailures.noItemFoundForIdFailure;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.validation.BlockRenewalValidator;
import org.folio.circulation.domain.validation.UserNotFoundValidator;
import org.folio.circulation.storage.ItemByIdInStorageFinder;
import org.folio.circulation.storage.SingleOpenLoanForItemInStorageFinder;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;

public class RenewByIdResource extends RenewalResource {
  public RenewByIdResource(HttpClient client) {
    super(client, "/circulation/renew-by-id");
  }

  @Override
  protected CompletableFuture<Result<Loan>> findLoan(
    JsonObject request,
    LoanRepository loanRepository,
    ItemRepository itemRepository,
    UserRepository userRepository,
    RequestQueueRepository requestQueueRepository) {

    final Result<RenewByIdRequest> requestResult
      = RenewByIdRequest.from(request);

    final String itemId = requestResult
      .map(RenewByIdRequest::getItemId)
      .orElse("unknown item ID");

    final UserNotFoundValidator userNotFoundValidator = new UserNotFoundValidator(
      userId -> singleValidationError("user is not found", "userId", userId));

    final SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder
      = new SingleOpenLoanForItemInStorageFinder(loanRepository, userRepository,
        moreThanOneOpenLoanFailure(itemId), false);

    final ItemByIdInStorageFinder itemFinder = new ItemByIdInStorageFinder(
      itemRepository, noItemFoundForIdFailure(itemId));

    final BlockRenewalValidator blockRenewalValidator =
      new BlockRenewalValidator(requestQueueRepository);

    return requestResult
      .after(checkInRequest -> itemFinder.findItemById(itemId))
      .thenComposeAsync(itemResult -> itemResult.after(blockRenewalValidator::refuseWhenFirstRequestIsRecall))
      .thenComposeAsync(itemResult -> itemResult.after(singleOpenLoanFinder::findSingleOpenLoan))
      .thenApply(userNotFoundValidator::refuseWhenUserNotFound)
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
