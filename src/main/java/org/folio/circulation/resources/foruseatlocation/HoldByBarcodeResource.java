package org.folio.circulation.resources.foruseatlocation;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAction;
import org.folio.circulation.domain.TimePeriod;
import org.folio.circulation.domain.policy.ExpirationDateManagement;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategy;
import org.folio.circulation.domain.representations.logs.LogEventType;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.SettingsRepository;
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
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.http.server.HttpResponse;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;
import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.determineClosedLibraryStrategyForHoldShelfExpirationDate;
import static org.folio.circulation.domain.representations.LoanProperties.*;
import static org.folio.circulation.resources.foruseatlocation.HoldByBarcodeRequest.loanIsNotForUseAtLocationFailure;
import static org.folio.circulation.resources.foruseatlocation.HoldByBarcodeRequest.noOpenLoanFailure;
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
    final var servicePointRepository = new ServicePointRepository(clients);
    final var settingsRepository = new SettingsRepository(clients);
    final var calendarRepository = new CalendarRepository(clients);
    final EventPublisher eventPublisher = new EventPublisher(webContext,clients);

    JsonObject requestBodyAsJson = routingContext.body().asJsonObject();

    HoldByBarcodeRequest.buildRequestFrom(requestBodyAsJson)
      .after(request -> findLoan(request, loanRepository, itemRepository, userRepository, errorHandler))
      .thenApply(HoldByBarcodeResource::failWhenOpenLoanNotFoundForItem)
      .thenApply(HoldByBarcodeResource::failWhenOpenLoanIsNotForUseAtLocation)
      .thenCompose(request -> request.after(req -> findPolicy(req, loanPolicyRepository)))
      .thenCompose(request -> request.after(req -> findServicePoint(req, servicePointRepository)))
      .thenCompose(request -> request.after(req -> findTenantTimeZone(req, settingsRepository)))
      .thenCompose(request -> request.after(req -> findHoldShelfExpirationDate(req, calendarRepository)))
      .thenApply(this::setStatusToHeldWithExpirationDate)
      .thenApply(this::setActionHeld)
      .thenCompose(request -> request.after(req -> loanRepository.updateLoan(req.getLoan())))
      .thenCompose(loanResult -> loanResult.after(
        loan -> eventPublisher.publishUsageAtLocationEvent(loan, LogEventType.LOAN)))
      .thenApply(loanResult -> loanResult.map(Loan::asJson).map(this::toResponse))
      .thenAccept(webContext::writeResultToHttpResponse);
  }

  protected CompletableFuture<Result<HoldByBarcodeRequest>> findLoan(HoldByBarcodeRequest request,
                                                                     LoanRepository loanRepository, ItemRepository itemRepository, UserRepository userRepository,
                                                                     CirculationErrorHandler errorHandler) {
    final ItemByBarcodeInStorageFinder itemFinder =
      new ItemByBarcodeInStorageFinder(itemRepository);

    final SingleOpenLoanForItemInStorageFinder loanFinder =
      new SingleOpenLoanForItemInStorageFinder(loanRepository, userRepository, false);

    return itemFinder.findItemByBarcode(request.getItemBarcode())
      .thenCompose(itemResult -> itemResult.after(loanFinder::findSingleOpenLoan)
        .thenApply(loanResult -> loanResult.map(request::withLoan))
        .thenApply(r -> errorHandler.handleValidationResult(r, FAILED_TO_FIND_SINGLE_OPEN_LOAN, request))
      );
  }

  protected CompletableFuture<Result<HoldByBarcodeRequest>> findPolicy(HoldByBarcodeRequest request, LoanPolicyRepository loanPolicies) {
    return loanPolicies.findPolicyForLoan(request.getLoan())
      .thenApply(loanResult -> loanResult.map(request::withLoan));
  }

  protected CompletableFuture<Result<HoldByBarcodeRequest>> findServicePoint(HoldByBarcodeRequest request, ServicePointRepository servicePoints) {
    return servicePoints.getServicePointById(request.getLoan().getCheckoutServicePointId())
      .thenApply(servicePoint -> servicePoint.map(request::withServicePoint));
  }

  protected CompletableFuture<Result<HoldByBarcodeRequest>> findTenantTimeZone(HoldByBarcodeRequest request, SettingsRepository settings) {
    return settings.lookupTimeZoneSettings()
      .thenApply(zoneId -> zoneId.map(request::withTenantTimeZone));
  }

  protected CompletableFuture<Result<HoldByBarcodeRequest>> findHoldShelfExpirationDate(HoldByBarcodeRequest request, CalendarRepository calendars) {
    Loan loan = request.getLoan();
    Period expiry = loan.getLoanPolicy().getHoldShelfExpiryPeriodForUseAtLocation();
    if (expiry == null) {
      log.warn("No hold shelf expiry period for use at location defined in loan policy {}", loan.getLoanPolicy().getName());
      return Result.ofAsync(request);
    } else {
      final ZonedDateTime baseExpirationDate = expiry.plusDate(ClockUtil.getZonedDateTime());
        TimePeriod timePeriod = loan.getLoanPolicy().getHoldShelfExpiryTimePeriodForUseAtLocation();
        ExpirationDateManagement expirationDateManagement = request.getServicePoint().getHoldShelfClosedLibraryDateManagement();
        ClosedLibraryStrategy strategy = determineClosedLibraryStrategyForHoldShelfExpirationDate(
          expirationDateManagement, baseExpirationDate, request.getTenantTimeZone(), timePeriod);

        return calendars.lookupOpeningDays(baseExpirationDate.withZoneSameInstant(request.getTenantTimeZone()).toLocalDate(),
            request.getServicePoint().getId())
          .thenApply(adjacentOpeningDaysResult -> strategy.calculateDueDate(baseExpirationDate, adjacentOpeningDaysResult.value()))
          .thenApply(dateTime -> dateTime.map(request::withHoldShelfExpirationDate));
    }
  }

  private Result<HoldByBarcodeRequest> setStatusToHeldWithExpirationDate(Result<HoldByBarcodeRequest> request) {
    return request.map (
      req -> req.withLoan(req.getLoan().changeStatusOfUsageAtLocation(USAGE_STATUS_HELD, req.getHoldShelfExpirationDate())));
  }


  private Result<HoldByBarcodeRequest> setActionHeld(Result<HoldByBarcodeRequest> request) {
    return request.map(req -> req.withLoan(req.getLoan().withAction(LoanAction.HELD_FOR_USE_AT_LOCATION)));
  }

  private static Result<HoldByBarcodeRequest> failWhenOpenLoanNotFoundForItem(Result<HoldByBarcodeRequest> request) {
     return request.failWhen(HoldByBarcodeRequest::loanIsNull, req -> noOpenLoanFailure(req).get());
  }

  private static Result<HoldByBarcodeRequest> failWhenOpenLoanIsNotForUseAtLocation (Result<HoldByBarcodeRequest> request) {
    return request.failWhen(HoldByBarcodeRequest::loanIsNotForUseAtLocation, req -> loanIsNotForUseAtLocationFailure(req).get());
  }

  private HttpResponse toResponse(JsonObject body) {
    return JsonHttpResponse.ok(body,
      format("/circulation/loans/%s", body.getString("id")));
  }


}
