package org.folio.circulation.resources;

import static org.folio.circulation.domain.validation.CommonFailures.moreThanOneOpenLoanFailure;
import static org.folio.circulation.domain.validation.CommonFailures.noItemFoundForBarcodeFailure;

import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.LoanCheckInService;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.LocationRepository;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.notice.PatronNoticeService;
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
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final LocationRepository locationRepository = LocationRepository.using(clients);

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
      servicePointRepository, patronNoticeService, userRepository);

    checkInRequestResult
      .map(CheckInProcessRecords::new)
      .combineAfter(processAdapter::findItem, CheckInProcessRecords::withItem)
      .thenComposeAsync(findItemResult -> findItemResult.combineAfter(
        processAdapter::findSingleOpenLoan, CheckInProcessRecords::withLoan))
      .thenComposeAsync(findLoanResult -> findLoanResult.combineAfter(
        processAdapter::checkInLoan, CheckInProcessRecords::withLoan))
      .thenComposeAsync(loanCheckInResult -> loanCheckInResult.combineAfter(
        processAdapter::getRequestQueue, CheckInProcessRecords::withRequestQueue))
      .thenComposeAsync(findRequestQueueResult -> findRequestQueueResult.combineAfter(
        processAdapter::updateRequestQueue, CheckInProcessRecords::withRequestQueue))
      .thenComposeAsync(updateRequestQueueResult -> updateRequestQueueResult.combineAfter(
        processAdapter::updateItem, CheckInProcessRecords::withItem))
      .thenComposeAsync(r -> r.after(locationRepository::loadLocation))
      .thenApply(handleItemStatus -> handleItemStatus.next(processAdapter::sendItemStatusPatronNotice))
      .thenComposeAsync(updateItemResult -> updateItemResult.combineAfter(
        processAdapter::getDestinationServicePoint, CheckInProcessRecords::withItem))
      .thenComposeAsync(updateItemResult -> updateItemResult.combineAfter(
        processAdapter::updateLoan, CheckInProcessRecords::withLoan))
      .thenApply(updateItemResult -> updateItemResult.next(processAdapter::sendCheckInPatronNotice))
      .thenApply(CheckInByBarcodeResponse::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }
}
