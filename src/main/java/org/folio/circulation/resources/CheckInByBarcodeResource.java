package org.folio.circulation.resources;

import static org.folio.circulation.domain.validation.CommonFailures.moreThanOneOpenLoanFailure;
import static org.folio.circulation.domain.validation.CommonFailures.noItemFoundForBarcodeFailure;

import org.folio.circulation.domain.AddressTypeRepository;
import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.LoanCheckInService;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.OverdueFineCalculatorService;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.domain.notice.schedule.RequestScheduledNoticeService;
import org.folio.circulation.domain.notice.session.PatronActionSessionService;
import org.folio.circulation.domain.policy.PatronNoticePolicyRepository;
import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.domain.representations.CheckInByBarcodeResponse;
import org.folio.circulation.storage.ItemByBarcodeInStorageFinder;
import org.folio.circulation.storage.SingleOpenLoanForItemInStorageFinder;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
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

    final LoanRepository loanRepository = new LoanRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final UserRepository userRepository = new UserRepository(clients);

    final AddressTypeRepository addressTypeRepository = new AddressTypeRepository(clients);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);

    final LoanCheckInService loanCheckInService = new LoanCheckInService();

    final UpdateItem updateItem = new UpdateItem(clients);
    final UpdateRequestQueue requestQueueUpdate = UpdateRequestQueue.using(clients);

    final Result<CheckInByBarcodeRequest> checkInRequestResult
      = CheckInByBarcodeRequest.from(routingContext.getBodyAsJson());

    final String itemBarcode = checkInRequestResult
      .map(CheckInByBarcodeRequest::getItemBarcode)
      .orElse("unknown barcode");

    final ItemByBarcodeInStorageFinder itemFinder = new ItemByBarcodeInStorageFinder(
      itemRepository, noItemFoundForBarcodeFailure(itemBarcode));

    final SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder
      = new SingleOpenLoanForItemInStorageFinder(loanRepository, userRepository,
        moreThanOneOpenLoanFailure(itemBarcode), true);

    final PatronNoticePolicyRepository patronNoticePolicyRepository = new PatronNoticePolicyRepository(clients);
    final PatronNoticeService patronNoticeService = new PatronNoticeService(patronNoticePolicyRepository, clients);

    final CheckInProcessAdapter processAdapter = new CheckInProcessAdapter(
      itemFinder, singleOpenLoanFinder, loanCheckInService,
      requestQueueRepository, updateItem, requestQueueUpdate, loanRepository,
      servicePointRepository, patronNoticeService, userRepository, addressTypeRepository);

    final RequestScheduledNoticeService requestScheduledNoticeService =
      RequestScheduledNoticeService.using(clients);

    final PatronActionSessionService patronActionSessionService =
      PatronActionSessionService.using(clients);

    final OverdueFineCalculatorService overdueFineCalculatorService =
      OverdueFineCalculatorService.using(clients);

    checkInRequestResult
      .map(CheckInProcessRecords::new)
      .combineAfter(processAdapter::findItem, CheckInProcessRecords::withItem)
      .thenComposeAsync(findItemResult -> findItemResult.combineAfter(
        processAdapter::findSingleOpenLoan, CheckInProcessRecords::withLoan))
      .thenComposeAsync(findLoanResult -> findLoanResult.combineAfter(
        processAdapter::checkInLoan, CheckInProcessRecords::withLoan))
      .thenComposeAsync(loanCheckInResult -> loanCheckInResult.combineAfter(
        processAdapter::getRequestQueue, CheckInProcessRecords::withRequestQueue))
      .thenApply(findRequestQueueResult -> findRequestQueueResult.map(
        processAdapter::setInHouseUse))
      .thenComposeAsync(inHouseUseResult -> inHouseUseResult.combineAfter(
        processAdapter::updateRequestQueue, CheckInProcessRecords::withRequestQueue))
      .thenApply(r -> r.map(records -> records.withLoggedInUserId(context.getUserId())))
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
