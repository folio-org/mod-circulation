package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.LoanRenewalService;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.representations.LoanResponse;
import org.folio.circulation.storage.SingleOpenLoanByUserAndItemBarcodeFinder;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

public class OverrideRenewalByBarcodeResource extends Resource {

  public OverrideRenewalByBarcodeResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/override-renewal-by-barcode", router);

    routeRegistration.create(this::renew);
  }

  private void renew(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true);
    final UserRepository userRepository = new UserRepository(clients);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final LoanRenewalService loanRenewalService = LoanRenewalService.using(clients);
    final SingleOpenLoanByUserAndItemBarcodeFinder loanFinder = new SingleOpenLoanByUserAndItemBarcodeFinder();

    final HttpResult<OverrideByBarcodeRequest> request = OverrideByBarcodeRequest.from(routingContext.getBodyAsJson());

    request.after(overrideRequest ->
      loanFinder.findLoan(routingContext.getBodyAsJson(), loanRepository, itemRepository, userRepository)
        .thenComposeAsync(r -> r.after(loan -> loanRenewalService.overrideRenewal(loan, overrideRequest.getDueDate(), overrideRequest.getComment())))
        .thenComposeAsync(r -> r.after(loanRepository::updateLoan)))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(LoanResponse::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }
}
