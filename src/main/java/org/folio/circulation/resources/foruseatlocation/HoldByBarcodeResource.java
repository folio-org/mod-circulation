package org.folio.circulation.resources.foruseatlocation;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAction;
import org.folio.circulation.domain.representations.logs.LogEventType;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.Resource;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.storage.ItemByBarcodeInStorageFinder;
import org.folio.circulation.storage.SingleOpenLoanForItemInStorageFinder;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.http.server.HttpResponse;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static java.lang.String.format;
import static org.folio.circulation.domain.representations.LoanProperties.USAGE_STATUS_HELD;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FIND_SINGLE_OPEN_LOAN;

public class HoldByBarcodeResource extends Resource {
  private static final String rootPath = "/circulation/hold-by-barcode-for-use-at-location";

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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
    final var loanPolicyRepository = new LoanPolicyRepository(clients);
    final EventPublisher eventPublisher = new EventPublisher(webContext,clients);

    JsonObject requestBodyAsJson = routingContext.body().asJsonObject();
    Result<HoldByBarcodeRequest> requestResult = HoldByBarcodeRequest.buildRequestFrom(requestBodyAsJson);

    requestResult
      .after(request -> findLoan(request, loanRepository, itemRepository, userRepository, errorHandler))
      .thenApply(loan -> failWhenOpenLoanNotFoundForItem(loan, requestResult.value()))
      .thenApply(loan -> failWhenOpenLoanIsNotForUseAtLocation(loan, requestResult.value()))
      .thenCompose(loanPolicyRepository::findPolicyForLoan)
      .thenApply(loanResult -> loanResult.map(loan -> loan.changeStatusOfUsageAtLocation(USAGE_STATUS_HELD)))
      .thenApply(loanResult -> loanResult.map(loan -> loan.withAction(LoanAction.HELD_FOR_USE_AT_LOCATION)))
      .thenCompose(loanResult -> loanResult.after(
          loan -> loanRepository.updateLoan(loanResult.value())))
      .thenCompose(loanResult -> loanResult.after(
        loan -> eventPublisher.publishUsageAtLocationEvent(loan, LogEventType.LOAN)))
      .thenApply(loanResult -> loanResult.map(Loan::asJson))
      .thenApply(loanAsJsonResult -> loanAsJsonResult.map(this::toResponse))
      .thenAccept(webContext::writeResultToHttpResponse);
  }

  protected CompletableFuture<Result<Loan>> findLoan(HoldByBarcodeRequest request,
    LoanRepository loanRepository, ItemRepository itemRepository, UserRepository userRepository,
    CirculationErrorHandler errorHandler) {

    final ItemByBarcodeInStorageFinder itemFinder =
      new ItemByBarcodeInStorageFinder(itemRepository);

    final SingleOpenLoanForItemInStorageFinder loanFinder =
      new SingleOpenLoanForItemInStorageFinder(loanRepository, userRepository, false);

    return itemFinder.findItemByBarcode(request.getItemBarcode())
      .thenCompose(itemResult -> itemResult.after(loanFinder::findSingleOpenLoan)
        .thenApply(r -> errorHandler.handleValidationResult(r, FAILED_TO_FIND_SINGLE_OPEN_LOAN, (Loan) null))
      );
  }

  private static Result<Loan> failWhenOpenLoanNotFoundForItem (Result<Loan> loanResult, HoldByBarcodeRequest request) {
    return loanResult.failWhen(HoldByBarcodeResource::loanIsNull, loan -> noOpenLoanFailure(request).get());
  }

  private Result<Loan> failWhenOpenLoanIsNotForUseAtLocation (Result<Loan> loanResult, HoldByBarcodeRequest request) {
    return loanResult.failWhen(HoldByBarcodeResource::loanIsNotForUseAtLocation, loan -> loanIsNotForUseAtLocationFailure(request).get());
  }

  private static Result<Boolean> loanIsNull (Loan loan) {
    return Result.succeeded(loan == null);
  }

  private static Result<Boolean> loanIsNotForUseAtLocation(Loan loan) {
    return Result.succeeded(!loan.isForUseAtLocation());
  }

  private static Supplier<HttpFailure> noOpenLoanFailure(HoldByBarcodeRequest request) {
    String message = "No open loan found for the item barcode.";
    log.warn(message);
    return () -> new BadRequestFailure(format(message + " (%s)", request.getItemBarcode()));
  }

  private static Supplier<HttpFailure> loanIsNotForUseAtLocationFailure(HoldByBarcodeRequest request) {
    String message = "The loan is open but is not for use at location.";
    log.warn(message);
    return () -> new BadRequestFailure(format(message + ", item barcode (%s)", request.getItemBarcode()));
  }

  private HttpResponse toResponse(JsonObject body) {
    return JsonHttpResponse.ok(body,
      format("/circulation/loans/%s", body.getString("id")));
  }


}
