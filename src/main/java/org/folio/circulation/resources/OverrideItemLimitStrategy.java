package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.domain.LoanAction.CHECKED_OUT_THROUGH_OVERRIDE;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class OverrideItemLimitStrategy extends ItemLimitHandlingStrategy {
  private static final String COMMENT = "comment";

  @Override
  public CompletableFuture<Result<Void>> handle(Loan loan, JsonObject request, Clients clients) {
    String comment = request.getString(COMMENT);

    if (isBlank(comment)) {
      return completedFuture(failedValidation(
        "Override request must contain a non-blank comment", COMMENT, comment));
    }

    if (itemLimitIsNotSet(loan)) {
      Map<String, String> errorParameters = new HashMap<>();
      errorParameters.put("loanPolicyName", loan.getLoanPolicy().getName());
      errorParameters.put("loanPolicyId", loan.getLoanPolicyId());
      errorParameters.put("itemLimit", null);

      return completedFuture(failedValidation(new ValidationError(
        "Item limit is not set in Loan Policy", errorParameters)));
    }

    return succeeded(null).afterWhen(
      r -> isLimitReached(loan, clients),
      r -> updateLoan(loan, comment),
      r -> fail(loan, false));
  }

  private CompletableFuture<Result<Void>> updateLoan(Loan loan, String comment) {
    loan.changeAction(CHECKED_OUT_THROUGH_OVERRIDE);
    loan.changeActionComment(comment);

    return doNothing();
  }
}
