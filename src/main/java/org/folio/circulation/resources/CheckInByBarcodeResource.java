package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;

import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.LOAN_DATE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.USER_BARCODE;
import static org.folio.circulation.domain.validation.CommonFailures.moreThanOneOpenLoanFailure;
import static org.folio.circulation.domain.validation.CommonFailures.noItemFoundForBarcodeFailure;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.Result.succeeded;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.CheckOutByBarcodeAction;
import org.folio.circulation.domain.LoanCheckInService;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestFulfilmentPreference;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.domain.notice.schedule.RequestScheduledNoticeService;
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

    final RequestScheduledNoticeService requestScheduledNoticeService =
      RequestScheduledNoticeService.using(clients);

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
        processAdapter::updateLoan, CheckInProcessRecords::withLoan))
      .thenApply(updateItemResult -> updateItemResult.next(processAdapter::sendCheckInPatronNotice))
      .thenApply(r -> r.next(requestScheduledNoticeService::rescheduleRequestNotices))
      .thenCompose(r -> r.after(
        records -> checkOutIfDelivery(records, clients, processAdapter)))
      .thenApply(CheckInByBarcodeResponse::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private CompletableFuture<Result<CheckInProcessRecords>> checkOutIfDelivery(
      CheckInProcessRecords records, Clients clients, CheckInProcessAdapter processAdapter) {
    // detect if Delivery Request is the topmost one
    // if Yes --> do Checkout
    // Otherwise --> do NOTHING

    return processAdapter.getRequestQueue(records)
      .thenApply(this::findHighestPriorityDeliveryRequest)
      .thenCompose(deliveryRequestResult -> deliveryRequestResult.after(
        request -> request
          .map(req -> checkOut(req, records, clients))
          .orElse(completedFuture(succeeded(records)))
      ));
  }

  private CompletableFuture<Result<CheckInProcessRecords>> checkOut(Request request, CheckInProcessRecords records,
      Clients clients) {
    JsonObject checkOutReq = new JsonObject();
    write(checkOutReq, ITEM_BARCODE, records.getItem().getBarcode());
    // take user from request
    write(checkOutReq, USER_BARCODE, request.getRequesterBarcode());
    write(checkOutReq, SERVICE_POINT_ID, records.getCheckInRequest().getServicePointId());
    write(checkOutReq, LOAN_DATE,  records.getCheckInRequest().getCheckInDate());

    CheckOutStrategy strategy = new RegularCheckOutStrategy();
    CheckOutByBarcodeAction cmd = new CheckOutByBarcodeAction(strategy, clients);

    return cmd.execute(checkOutReq)
      .thenApply(loanResult -> {
        // as an option update item in check in req from check out item
        return Result.succeeded(records);
      });
  }

  private Result<Optional<Request>> findHighestPriorityDeliveryRequest(Result<RequestQueue> requestQueueResult) {
    return requestQueueResult.next(requestQueue -> {
      Optional<Request> deliveryRequest = requestQueue.getRequests().stream()
        .findFirst()
        .filter(request -> request.getFulfilmentPreference().equals(RequestFulfilmentPreference.DELIVERY));

      return succeeded(deliveryRequest);
    });
  }
}
