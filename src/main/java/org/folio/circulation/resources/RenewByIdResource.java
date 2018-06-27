package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.support.HttpResult;

import java.util.concurrent.CompletableFuture;

public class RenewByIdResource extends RenewalResource {
  public RenewByIdResource(HttpClient client) {
    super(client, "/circulation/renew-by-id");
  }

  @Override
  protected CompletableFuture<HttpResult<Loan>> findLoan(
    JsonObject request,
    LoanRepository loanRepository) {

    return RenewByIdRequest.from(request)
      .after(loanRepository::findOpenLoanById);
  }
}
