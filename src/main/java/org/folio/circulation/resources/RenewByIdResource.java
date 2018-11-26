package org.folio.circulation.resources;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.support.HttpResult;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;

public class RenewByIdResource extends RenewalResource {
  public RenewByIdResource(HttpClient client) {
    super(client, "/circulation/renew-by-id");
  }

  @Override
  protected CompletableFuture<HttpResult<Loan>> findLoan(
    JsonObject request,
    LoanRepository loanRepository) {

    final HttpResult<RenewByIdRequest> requestResult
      = RenewByIdRequest.from(request);

    return requestResult
      .after(query -> loanRepository.findOpenLoanById(query.getItemId()))
      .thenApply(loanResult -> loanResult.combineToResult(requestResult,
        this::refuseWhenUserDoesNotMatch));
  }

  private HttpResult<Loan> refuseWhenUserDoesNotMatch(
    Loan loan,
    RenewByIdRequest idRequest) {

    if(userMatches(loan, idRequest.getUserId())) {
      return succeeded(loan);
    }
    else {
      return failed(failure("Cannot renew item checked out to different user",
        RenewByIdRequest.USER_ID, idRequest.getUserId()));
    }
  }

  private boolean userMatches(Loan loan, String expectedUserId) {
    return StringUtils.equals(loan.getUser().getId(), expectedUserId);
  }
}
