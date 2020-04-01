package org.folio.circulation.resources;

import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import org.folio.circulation.domain.ClaimItemReturnedRequest;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class ClaimItemReturnedResource extends ChangeStatusResource<ClaimItemReturnedRequest> {
  public static final String ITEM_CLAIMED_RETURNED_DATE = "itemClaimedReturnedDateTime";

  public ClaimItemReturnedResource(HttpClient client) {
    super(client, "/circulation/loans/:id/claim-item-returned");
  }

  @Override
  protected Loan changeLoanAndItemStatus(Loan loan, ClaimItemReturnedRequest request) {
    return loan.claimItemReturned(request.getComment(), request
      .getItemClaimedReturnedDateTime());
  }

  @Override
  protected Result<ClaimItemReturnedRequest> createItemStatusChangeRequest(RoutingContext routingContext) {
    final String loanId = routingContext.pathParam("id");
    final JsonObject body = routingContext.getBodyAsJson();

    if (!body.containsKey(ITEM_CLAIMED_RETURNED_DATE)) {
      return failed(singleValidationError("Item claimed returned date is a required field",
        ITEM_CLAIMED_RETURNED_DATE, null));
    }

    return succeeded(new ClaimItemReturnedRequest(
      loanId, DateTime.parse(body.getString(ITEM_CLAIMED_RETURNED_DATE)),
      body.getString(COMMENT)));
  }
}
