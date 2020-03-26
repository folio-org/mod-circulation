package org.folio.circulation.resources;

import static org.folio.circulation.domain.validation.NotInItemStatusValidator.refuseWhenItemIsNotClaimedReturned;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.representations.ChangeItemStatusRequest;
import org.folio.circulation.support.Result;

import java.util.concurrent.CompletableFuture;

public class MarkItemMissingResource extends ChangeStatusResource {
  public static final String COMMENT = "comment";

  public MarkItemMissingResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    router.post("/circulation/loans/:id/mark-item-missing")
      .handler(this::changeItemStatus);
  }

  protected CompletableFuture<Result<Loan>> validate(Result<Loan> loanResult){
    return super.validate(loanResult)
      .thenApply(loan-> refuseWhenItemIsNotClaimedReturned(loan));
}

  @Override
  protected Loan changeLoanAndItemStatus(Loan loan, ChangeItemStatusRequest request) {
    return loan.markItemMissing(request.getComment());
  }

  @Override
  protected Result<ChangeItemStatusRequest> createItemStatusChangeRequest(
    RoutingContext routingContext) {

    final String loanId = routingContext.pathParam("id");
    final JsonObject body = routingContext.getBodyAsJson();

    if (!body.containsKey(COMMENT)) {
      return failed(singleValidationError("Comment is a required field",
        COMMENT, null));
    }

    return succeeded(new ChangeItemStatusRequest(loanId, body.getString(COMMENT)));
  }
}
