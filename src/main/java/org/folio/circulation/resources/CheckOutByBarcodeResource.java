package org.folio.circulation.resources;

import static org.folio.circulation.support.Result.failed;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import org.folio.circulation.domain.CheckOutByBarcodeAction;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CreatedJsonResponseResult;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

public class CheckOutByBarcodeResource extends Resource {

  private final String rootPath;
  private final CheckOutStrategy checkOutStrategy;

  public CheckOutByBarcodeResource(String rootPath, HttpClient client, CheckOutStrategy checkOutStrategy) {
    super(client);
    this.rootPath = rootPath;
    this.checkOutStrategy = checkOutStrategy;
  }

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      rootPath, router);

    routeRegistration.create(this::checkOut);
  }

  private void checkOut(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject request = routingContext.getBodyAsJson();
    final Clients clients = Clients.create(context, client);

    CheckOutByBarcodeAction cmd = new CheckOutByBarcodeAction(checkOutStrategy, clients);

    cmd.execute(request)
      .thenApply(this::toExtendedLoan)
      .thenApply(this::createdLoanFrom)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private Result<JsonObject> toExtendedLoan(Result<Loan> r) {
    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    return r.map(loanRepresentation::extendedLoan);
  }

  private ResponseWritableResult<JsonObject> createdLoanFrom(Result<JsonObject> result) {
    if (result.failed()) {
      return failed(result.cause());
    } else {
      return new CreatedJsonResponseResult(result.value(),
        String.format("/circulation/loans/%s", result.value().getString("id")));
    }
  }

}
