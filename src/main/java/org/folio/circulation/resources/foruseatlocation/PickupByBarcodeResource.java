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
import org.folio.circulation.storage.SingleOpenLoanByUserAndItemBarcodeFinder;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.http.server.HttpResponse;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static java.lang.String.format;
import static org.folio.circulation.domain.representations.LoanProperties.USAGE_STATUS_IN_USE;
import static org.folio.circulation.resources.foruseatlocation.PickupByBarcodeRequest.buildRequestFrom;

import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FIND_SINGLE_OPEN_LOAN;

public class PickupByBarcodeResource extends Resource {

  private static final String rootPath = "/circulation/pickup-by-barcode-for-use-at-location";

  public PickupByBarcodeResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration(rootPath, router).create(this::markInUse);
  }

  private void markInUse(RoutingContext routingContext) {
    final WebContext webContext = new WebContext(routingContext);
    final Clients clients = Clients.create(webContext, client);
    final OkapiPermissions okapiPermissions = OkapiPermissions.from(webContext.getHeaders());
    final CirculationErrorHandler errorHandler = new OverridingErrorHandler(okapiPermissions);
    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    JsonObject requestBodyAsJson = routingContext.body().asJsonObject();
    Result<PickupByBarcodeRequest> pickupByBarcodeRequest = buildRequestFrom(requestBodyAsJson);

    pickupByBarcodeRequest
      .after(request -> findLoan(request, loanRepository, itemRepository, userRepository, errorHandler))
      .thenApply(loan -> failWhenOpenLoanForItemAndUserNotFound(loan, pickupByBarcodeRequest.value()))
      .thenApply(loan -> failWhenOpenLoanIsNotForUseAtLocation(loan, pickupByBarcodeRequest.value()))
      .thenApply(loanResult -> loanResult.map(loan ->
        loan.changeStatusOfUsageAtLocation(USAGE_STATUS_IN_USE)
          .withAction(LoanAction.PICKED_UP_FOR_USE_AT_LOCATION)))
      .thenCompose(loanResult -> loanResult.after(
        loan -> loanRepository.updateLoan(loanResult.value())))
      .thenCompose(loanResult -> loanResult.after(
        loan -> eventPublisher.publishUsageAtLocationEvent(loan, LogEventType.PICKED_UP_FOR_USE_AT_LOCATION)))
      .thenApply(loanResult -> loanResult.map(Loan::asJson))
      .thenApply(jsonResult -> jsonResult.map(this::toResponse))
      .thenAccept(webContext::writeResultToHttpResponse);
  }

  protected CompletableFuture<Result<Loan>> findLoan(PickupByBarcodeRequest request,
                                                     LoanRepository loanRepository,
                                                     ItemRepository itemRepository,
                                                     UserRepository userRepository,
                                                     CirculationErrorHandler errorHandler) {

    final SingleOpenLoanByUserAndItemBarcodeFinder loanFinder
      = new SingleOpenLoanByUserAndItemBarcodeFinder(loanRepository,
      itemRepository, userRepository);

    return loanFinder.findLoan(request.getItemBarcode(), request.getUserBarcode())
        .thenApply(r -> errorHandler.handleValidationResult(r, FAILED_TO_FIND_SINGLE_OPEN_LOAN, r.value()));
  }

  private static Result<Loan> failWhenOpenLoanForItemAndUserNotFound (Result<Loan> loanResult, PickupByBarcodeRequest request) {
    return loanResult.failWhen(PickupByBarcodeResource::loanIsNull, loan -> noOpenLoanFailure(request).get());
  }

  private static Result<Loan> failWhenOpenLoanIsNotForUseAtLocation (Result<Loan> loanResult, PickupByBarcodeRequest request) {
    return loanResult.failWhen(PickupByBarcodeResource::loanIsNotForUseAtLocation, loan -> loanIsNotForUseAtLocationFailure(request).get());
  }

  private static Result<Boolean> loanIsNull (Loan loan) {
    return Result.succeeded(loan == null);
  }

  private static Result<Boolean> loanIsNotForUseAtLocation(Loan loan) {
    return Result.succeeded(!loan.isForUseAtLocation());
  }

  private static Supplier<HttpFailure> noOpenLoanFailure(PickupByBarcodeRequest request) {
    return () -> new BadRequestFailure(
      format("No open loan found for item barcode (%s) and user (%s)",
        request.getItemBarcode(), request.getUserBarcode())
    );
  }

  private static Supplier<HttpFailure> loanIsNotForUseAtLocationFailure(PickupByBarcodeRequest request) {
    return () -> new BadRequestFailure(
      format("The loan is open but is not for use at location, item barcode (%s)", request.getItemBarcode())
    );
  }

  private HttpResponse toResponse(JsonObject body) {
    return JsonHttpResponse.ok(body,
      format("/circulation/loans/%s", body.getString("id")));
  }


}
