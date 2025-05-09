package org.folio.circulation.resources.foruseatlocation;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAction;
import org.folio.circulation.domain.representations.logs.LogEventType;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.http.server.WebContext;

import static org.folio.circulation.domain.representations.LoanProperties.USAGE_STATUS_HELD;

public class HoldByBarcodeResource extends UsageStatusChangeResource {

  public HoldByBarcodeResource(HttpClient client) {
    super("/circulation/hold-by-barcode-for-use-at-location",client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration(rootPath, router).create(this::markHeld);
  }

  private void markHeld(RoutingContext routingContext) {
    final WebContext webContext = new WebContext(routingContext);
    final Clients clients = Clients.create(webContext, client);
    final OkapiPermissions okapiPermissions = OkapiPermissions.from(webContext.getHeaders());
    final CirculationErrorHandler errorHandler = new OverridingErrorHandler(okapiPermissions);
    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    JsonObject bodyAsJson = routingContext.body().asJsonObject();

    findLoan(bodyAsJson, loanRepository, itemRepository, userRepository, errorHandler)
      .thenApply(loanResult -> loanResult.map(loan -> loan.changeStatusOfUsageAtLocation(USAGE_STATUS_HELD)))
      .thenApply(loanResult -> loanResult.map(loan -> loan.withAction(LoanAction.HELD_FOR_USE_AT_LOCATION)))
      .thenComposeAsync(loanResult -> loanResult.after(
        loan -> eventPublisher.publishUsageAtLocationEvent(loan, LogEventType.HELD_FOR_USE_AT_LOCATION)))
      .thenComposeAsync(loanResult -> loanRepository.updateLoan(loanResult.value()))
      .thenApply(loanResult -> loanResult.map(Loan::asJson))
      .thenApply(loanAsJsonResult -> loanAsJsonResult.map(this::toResponse))
      .thenAccept(webContext::writeResultToHttpResponse);;
  }


}
