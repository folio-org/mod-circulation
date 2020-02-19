package org.folio.circulation.resources;

import static org.folio.circulation.domain.validation.UserNotFoundValidator.refuseWhenLoggedInUserNotPresent;

import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.OverdueFineCalculatorService;
import org.folio.circulation.domain.notice.schedule.RequestScheduledNoticeService;
import org.folio.circulation.domain.notice.session.PatronActionSessionService;
import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.domain.representations.CheckInByBarcodeResponse;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
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

    final Result<CheckInByBarcodeRequest> checkInRequestResult
      = CheckInByBarcodeRequest.from(routingContext.getBodyAsJson());

    final CheckInProcessAdapter processAdapter = CheckInProcessAdapter.newInstance(clients);

    final RequestScheduledNoticeService requestScheduledNoticeService =
      RequestScheduledNoticeService.using(clients);

    final PatronActionSessionService patronActionSessionService =
      PatronActionSessionService.using(clients);

    final OverdueFineCalculatorService overdueFineCalculatorService =
      OverdueFineCalculatorService.using(clients);

    refuseWhenLoggedInUserNotPresent(context)
      .next(notUsed -> checkInRequestResult)
      .map(CheckInProcessRecords::new)
      .combineAfter(processAdapter::findItem, CheckInProcessRecords::withItem)
      .thenComposeAsync(findItemResult -> findItemResult.combineAfter(
        processAdapter::getRequestQueue, CheckInProcessRecords::withRequestQueue))
      .thenApply(findRequestQueueResult -> findRequestQueueResult.map(
        processAdapter::setInHouseUse))
      .thenApplyAsync(r -> r.map(records -> records.withLoggedInUserId(context.getUserId())))
      .thenComposeAsync(setUserResult -> setUserResult.after(processAdapter::logCheckInOperation))
      .thenComposeAsync(logCheckInResult -> logCheckInResult.combineAfter(
        processAdapter::findSingleOpenLoan, CheckInProcessRecords::withLoan))
      .thenComposeAsync(findLoanResult -> findLoanResult.combineAfter(
        processAdapter::checkInLoan, CheckInProcessRecords::withLoan))
      .thenComposeAsync(checkInLoan -> checkInLoan.combineAfter(
        processAdapter::updateRequestQueue, CheckInProcessRecords::withRequestQueue))
      .thenComposeAsync(updateRequestQueueResult -> updateRequestQueueResult.combineAfter(
        processAdapter::updateItem, CheckInProcessRecords::withItem))
      .thenApply(handleItemStatus -> handleItemStatus.next(processAdapter::sendItemStatusPatronNotice))
      .thenComposeAsync(updateItemResult -> updateItemResult.combineAfter(
        processAdapter::getDestinationServicePoint, CheckInProcessRecords::withItem))
      .thenComposeAsync(updateItemResult -> updateItemResult.combineAfter(
        processAdapter::getCheckInServicePoint, CheckInProcessRecords::withCheckInServicePoint))
      .thenComposeAsync(updateItemResult -> updateItemResult.combineAfter(
        processAdapter::getPickupServicePoint, CheckInProcessRecords::withHighestPriorityFulfillableRequest))
      .thenComposeAsync(updateItemResult -> updateItemResult.combineAfter(
        processAdapter::getRequester, CheckInProcessRecords::withHighestPriorityFulfillableRequest))
      .thenComposeAsync(updateItemResult -> updateItemResult.combineAfter(
        processAdapter::getAddressType, CheckInProcessRecords::withHighestPriorityFulfillableRequest))
      .thenComposeAsync(updateItemResult -> updateItemResult.combineAfter(
        processAdapter::updateLoan, CheckInProcessRecords::withLoan))
      .thenComposeAsync(updateItemResult -> updateItemResult.after(
        patronActionSessionService::saveCheckInSessionRecord))
      .thenComposeAsync(r -> r.after(overdueFineCalculatorService::calculateOverdueFine))
      .thenApply(r -> r.next(requestScheduledNoticeService::rescheduleRequestNotices))
      .thenApply(CheckInByBarcodeResponse::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }
}
