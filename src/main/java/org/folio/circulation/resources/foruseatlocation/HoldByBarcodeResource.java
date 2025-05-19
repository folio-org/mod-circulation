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
import org.folio.circulation.resources.Resource;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.storage.ItemByBarcodeInStorageFinder;
import org.folio.circulation.storage.SingleOpenLoanForItemInStorageFinder;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.http.server.HttpResponse;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.domain.representations.LoanProperties.USAGE_STATUS_HELD;
import static org.folio.circulation.resources.foruseatlocation.HoldByBarcodeRequest.holdByBarcodeRequestFrom;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FIND_SINGLE_OPEN_LOAN;

public class HoldByBarcodeResource extends Resource {
  private static final String rootPath = "/circulation/hold-by-barcode-for-use-at-location";

  public HoldByBarcodeResource(HttpClient client) {
    super(client);
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

  protected CompletableFuture<Result<Loan>> findLoan(JsonObject request,
                                                     LoanRepository loanRepository,
                                                     ItemRepository itemRepository,
                                                     UserRepository userRepository,
                                                     CirculationErrorHandler errorHandler) {

    final ItemByBarcodeInStorageFinder itemFinder =
      new ItemByBarcodeInStorageFinder(itemRepository);

    final SingleOpenLoanForItemInStorageFinder loanFinder =
      new SingleOpenLoanForItemInStorageFinder(loanRepository, userRepository, false);

    return holdByBarcodeRequestFrom(request)
      .after(shelfRequest -> itemFinder.findItemByBarcode(shelfRequest.getItemBarcode()))
      .thenCompose(itemResult -> itemResult.after(loanFinder::findSingleOpenLoan)
        .thenApply(r ->
          errorHandler.handleValidationResult(r, FAILED_TO_FIND_SINGLE_OPEN_LOAN, (Loan) null))
      );
  }

  private HttpResponse toResponse(JsonObject body) {
    return JsonHttpResponse.ok(body,
      String.format("/circulation/loans/%s", body.getString("id")));
  }


}
