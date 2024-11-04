package org.folio.circulation.resources;

import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.domain.validation.UserNotFoundValidator.refuseWhenLoggedInUserNotPresent;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.notice.schedule.RequestScheduledNoticeService;
import org.folio.circulation.domain.notice.session.PatronActionSessionService;
import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.domain.representations.CheckInByBarcodeResponse;
import org.folio.circulation.domain.validation.CheckInValidators;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.sessions.PatronActionSessionRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class CheckInByBarcodeResource extends Resource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public CheckInByBarcodeResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/check-in-by-barcode", router);

    routeRegistration.create(this::checkIn);
  }

  private void checkIn(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    final Clients clients = Clients.create(context, client);

    final var userRepository = new UserRepository(clients);
    final var itemRepository = new ItemRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final var requestRepository = RequestRepository.using(clients,
      itemRepository, userRepository, loanRepository);

    final Result<CheckInByBarcodeRequest> checkInRequestResult
      = CheckInByBarcodeRequest.from(routingContext.getBodyAsJson());

    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    final var checkInValidators = new CheckInValidators(this::errorWhenInIncorrectStatus);
    final CheckInProcessAdapter processAdapter = CheckInProcessAdapter.newInstance(clients,
      itemRepository, userRepository, loanRepository, requestRepository,
      new RequestQueueRepository(requestRepository));

    final RequestScheduledNoticeService requestScheduledNoticeService =
      RequestScheduledNoticeService.using(clients);

    final PatronActionSessionService patronActionSessionService =
      PatronActionSessionService.using(clients,
        PatronActionSessionRepository.using(clients, loanRepository, userRepository));

    final RequestNoticeSender requestNoticeSender = RequestNoticeSender.using(clients);

    final ConfigurationRepository configurationRepository = new ConfigurationRepository(clients);

    refuseWhenLoggedInUserNotPresent(context)
      .next(notUsed -> checkInRequestResult)
      .map(CheckInContext::new)
      .combineAfter(processAdapter::findItem, (records, item) -> records
        .withItemAndUpdatedLoan(item)
        .withItemStatusBeforeCheckIn(item.getStatus()))
      .thenApply(checkInValidators::refuseWhenItemIsNotAllowedForCheckIn)
      .thenApply(checkInValidators::refuseWhenClaimedReturnedIsNotResolved)
      .thenComposeAsync(r -> r.combineAfter(configurationRepository::lookupTlrSettings,
        CheckInContext::withTlrSettings))
      .thenComposeAsync(r -> r.combineAfter(configurationRepository::findTimeZoneConfiguration,
        CheckInContext::withTimeZone))
      .thenComposeAsync(findItemResult -> findItemResult.combineAfter(
        processAdapter::getRequestQueue, CheckInContext::withRequestQueue))
      .thenComposeAsync(r -> r.after(processAdapter::findFulfillableRequest))
      .thenApply(findRequestQueueResult -> findRequestQueueResult.map(
        processAdapter::setInHouseUse))
      .thenApplyAsync(r -> r.map(records -> records.withLoggedInUserId(context.getUserId())))
      .thenComposeAsync(setUserResult -> setUserResult.after(processAdapter::logCheckInOperation))
      .thenComposeAsync(logCheckInResult -> logCheckInResult.combineAfter(
        processAdapter::findSingleOpenLoan, CheckInContext::withLoan))
      .thenComposeAsync(findLoanResult -> findLoanResult.combineAfter(
        processAdapter::checkInLoan, CheckInContext::withLoan))
      .thenComposeAsync(checkInLoan -> checkInLoan.combineAfter(
        processAdapter::updateRequestQueue, CheckInContext::withRequestQueue))
        .thenComposeAsync(r -> r.after(processAdapter::findFulfillableRequest))
      .thenComposeAsync(checkInContextResult ->
        checkInContextResult.combineAfter(processAdapter::findFloatingDestination,
          CheckInContext::withItemAndUpdatedLoan))
      .thenComposeAsync(updateRequestQueueResult -> updateRequestQueueResult.combineAfter(
        processAdapter::updateItem, CheckInContext::withItemAndUpdatedLoan))
      .thenApply(handleItemStatus -> handleItemStatus.next(
        requestNoticeSender::sendNoticeOnRequestAwaitingPickup))
      .thenComposeAsync(updateItemResult -> updateItemResult.combineAfter(
        processAdapter::getDestinationServicePoint, CheckInContext::withItemAndUpdatedLoan))
      .thenComposeAsync(updateItemResult -> updateItemResult.combineAfter(
        processAdapter::getCheckInServicePoint, CheckInContext::withCheckInServicePoint))
      .thenComposeAsync(updateItemResult -> updateItemResult.combineAfter(
        processAdapter::getPickupServicePoint, CheckInContext::withHighestPriorityFulfillableRequest))
      .thenComposeAsync(updateItemResult -> updateItemResult.combineAfter(
        processAdapter::getRequester, CheckInContext::withHighestPriorityFulfillableRequest))
      .thenComposeAsync(updateItemResult -> updateItemResult.combineAfter(
        processAdapter::getAddressType, CheckInContext::withHighestPriorityFulfillableRequest))
      .thenComposeAsync(updateItemResult -> updateItemResult.combineAfter(
        processAdapter::updateLoan, CheckInContext::withLoan))
      .thenComposeAsync(updateItemResult -> updateItemResult.after(
        patronActionSessionService::saveCheckInSessionRecord))
      .thenComposeAsync(r -> r.after(processAdapter::refundLostItemFees))
      .thenComposeAsync(r -> r.after(
        records -> processAdapter.createOverdueFineIfNecessary(records, context)))
      .thenComposeAsync(r -> r.after(v -> eventPublisher.publishItemCheckedInEvents(v, userRepository, loanRepository)))
      .thenApply(r -> r.next(requestScheduledNoticeService::rescheduleRequestNotices))
      .thenApply(r -> r.map(CheckInByBarcodeResponse::fromRecords))
      .thenApply(r -> r.map(CheckInByBarcodeResponse::toHttpResponse))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private ValidationErrorFailure errorWhenInIncorrectStatus(Item item) {
    log.debug("errorWhenInIncorrectStatus:: parameters item: {}", () -> item);
    String message =
      String.format("%s (%s) (Barcode: %s) has the item status %s and cannot be checked in",
        item.getTitle(),
        item.getMaterialTypeName(),
        item.getBarcode(),
        item.getStatusName());

    return singleValidationError(message, ITEM_BARCODE, item.getBarcode());
  }
}
