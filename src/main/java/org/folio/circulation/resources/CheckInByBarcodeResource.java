package org.folio.circulation.resources;

import static org.folio.circulation.domain.validation.UserNotFoundValidator.refuseWhenLoggedInUserNotPresent;

import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.notice.schedule.RequestScheduledNoticeService;
import org.folio.circulation.domain.notice.session.PatronActionSessionService;
import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.domain.representations.CheckInByBarcodeResponse;
import org.folio.circulation.domain.validation.CheckInValidators;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
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

    routeRegistration.create(this::checkIn);
  }

  private void checkIn(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    final Clients clients = Clients.create(context, client);

    final Result<CheckInByBarcodeRequest> checkInRequestResult
      = CheckInByBarcodeRequest.from(routingContext.getBodyAsJson());

    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    final CheckInProcessAdapter processAdapter = CheckInProcessAdapter.newInstance(clients);

    final RequestScheduledNoticeService requestScheduledNoticeService =
      RequestScheduledNoticeService.using(clients);

    final PatronActionSessionService patronActionSessionService =
      PatronActionSessionService.using(clients);

    refuseWhenLoggedInUserNotPresent(context)
      .next(notUsed -> checkInRequestResult)
      .map(CheckInContext::new)
      .combineAfter(processAdapter::findItem, (records, item) -> records
        .withItem(item)
        .withItemStatusBeforeCheckIn(item.getStatus()))
      .thenApply(CheckInValidators::refuseWhenClaimedReturnedIsNotResolved)
      .thenComposeAsync(findItemResult -> findItemResult.combineAfter(
        processAdapter::getRequestQueue, CheckInContext::withRequestQueue))
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
      .thenComposeAsync(updateRequestQueueResult -> updateRequestQueueResult.combineAfter(
        processAdapter::updateItem, CheckInContext::withItem))
      .thenApply(handleItemStatus -> handleItemStatus.next(processAdapter::sendItemStatusPatronNotice))
      .thenComposeAsync(updateItemResult -> updateItemResult.combineAfter(
        processAdapter::getDestinationServicePoint, CheckInContext::withItem))
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
      .thenComposeAsync(r -> r.after(eventPublisher::publishItemCheckedInEvents))
      .thenApply(r -> r.next(requestScheduledNoticeService::rescheduleRequestNotices))
      .thenApply(r -> r.map(CheckInByBarcodeResponse::fromRecords))
      .thenApply(r -> r.map(CheckInByBarcodeResponse::toHttpResponse))
      .thenAccept(context::writeResultToHttpResponse);
  }
}
