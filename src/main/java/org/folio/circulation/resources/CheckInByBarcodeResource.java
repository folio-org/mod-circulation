package org.folio.circulation.resources;

import org.folio.circulation.domain.LoanCheckinService;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.domain.representations.LoanResponse;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class CheckInByBarcodeResource extends Resource {
  public CheckInByBarcodeResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/check-in-by-barcode", router);

    routeRegistration.create(this::checkin);
  }

  private void checkin(RoutingContext routingContext) {

    final WebContext context = new WebContext(routingContext);

    final Clients clients = Clients.create(context, client);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final LoanCheckinService loanCheckinService = new LoanCheckinService();

    final UpdateItem updateItem = new UpdateItem(clients);

    // TODO: Validation check for same user should be in the domain service

    CheckInByBarcodeRequest.from(routingContext.getBodyAsJson())
      .after(loanRepository::findOpenLoanByBarcode)
      .thenApply(r -> r.next(loanCheckinService::checkin))
      .thenComposeAsync(r -> r.after(updateItem::setLoansItemStatusAvaliable))
      .thenComposeAsync(r -> r.after(loanRepository::updateLoan))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(LoanResponse::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }
}
