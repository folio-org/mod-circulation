package org.folio.circulation.resources;

import static org.folio.circulation.resources.OverrideByBarcodeRequest.renewalOverrideRequestFrom;

import org.folio.circulation.domain.LoanRenewalService;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.RequestRepository;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticeService;
import org.folio.circulation.domain.representations.LoanResponse;
import org.folio.circulation.storage.SingleOpenLoanByUserAndItemBarcodeFinder;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

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
    final ItemRepository itemRepository = new ItemRepository(clients, true, true, false);
    final UserRepository userRepository = new UserRepository(clients);
    final RequestRepository requestRepository = RequestRepository.using(clients);
    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final LoanRenewalService renewalService = LoanRenewalService.using(clients);
    final SingleOpenLoanByUserAndItemBarcodeFinder loanFinder
      = new SingleOpenLoanByUserAndItemBarcodeFinder(loanRepository,
      itemRepository, userRepository, requestRepository);
    final ScheduledNoticeService scheduledNoticeService = ScheduledNoticeService.using(clients);

    renewalOverrideRequestFrom(routingContext.getBodyAsJson())
      .after(override -> loanFinder.findLoan(override.getItemBarcode(), override.getUserBarcode())
      .thenComposeAsync(r -> r.after(loan -> renewalService.overrideRenewal(loan, override.getDueDate(), override.getComment())))
      .thenComposeAsync(r -> r.after(loanRepository::updateLoan))
      .thenComposeAsync(r -> r.after(scheduledNoticeService::rescheduleDueDateNotices)))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(LoanResponse::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }
}
