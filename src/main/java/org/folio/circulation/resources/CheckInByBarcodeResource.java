package org.folio.circulation.resources;

import static org.folio.circulation.domain.validation.CommonFailures.moreThanOneOpenLoanFailure;
import static org.folio.circulation.domain.validation.CommonFailures.noItemFoundFailure;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanCheckInService;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.domain.representations.CheckInByBarcodeResponse;
import org.folio.circulation.storage.ItemByBarcodeInStorageFinder;
import org.folio.circulation.storage.SingleOpenLoanForItemInStorageFinder;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ItemRepository;
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
    final ItemRepository itemRepository = new ItemRepository(clients, true, true);
    final UserRepository userRepository = new UserRepository(clients);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final LoanCheckInService loanCheckInService = new LoanCheckInService();

    final UpdateItem updateItem = new UpdateItem(clients);
    final UpdateRequestQueue requestQueueUpdate = UpdateRequestQueue.using(clients);

    // TODO: Validation check for same user should be in the domain service

    final HttpResult<CheckInByBarcodeRequest> checkInRequestResult
      = CheckInByBarcodeRequest.from(routingContext.getBodyAsJson());

    checkInRequestResult
      .after(checkInRequest -> findOpenLoanByBarcode(
        itemRepository, loanRepository, userRepository, checkInRequest.getItemBarcode()))
      .thenApply(loanResult -> loanResult.combineToResult(checkInRequestResult,
        loanCheckInService::checkIn))
      .thenComposeAsync(loanResult -> loanResult.combineAfter(
        loan -> requestQueueRepository.get(loan.getItemId()), mapToRelatedRecords()))
      .thenComposeAsync(result -> result.after(requestQueueUpdate::onCheckIn))
      .thenComposeAsync(result -> result.after(updateItem::onLoanUpdate))
      // Loan must be updated after item
      // due to snapshot of item status stored with the loan
      // as this is how the loan action history is populated
      .thenComposeAsync(result -> result.after(loanRepository::updateLoan))
      .thenApply(result -> result.map(LoanAndRelatedRecords::getLoan))
      .thenApply(result -> result.map(loanRepresentation::extendedLoan))
      .thenApply(CheckInByBarcodeResponse::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private BiFunction<Loan, RequestQueue, LoanAndRelatedRecords> mapToRelatedRecords() {
    return (loan, requestQueue) -> new LoanAndRelatedRecords(loan).withRequestQueue(requestQueue);
  }

  private CompletableFuture<HttpResult<Loan>> findOpenLoanByBarcode(
    ItemRepository itemRepository,
    LoanRepository loanRepository,
    UserRepository userRepository,
    String itemBarcode) {

    final ItemByBarcodeInStorageFinder itemFinder = new ItemByBarcodeInStorageFinder(
      itemRepository, noItemFoundFailure(itemBarcode));

    final SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder
      = new SingleOpenLoanForItemInStorageFinder(loanRepository, userRepository,
        moreThanOneOpenLoanFailure(itemBarcode));

    return itemFinder.findItemByBarcode(itemBarcode)
      .thenComposeAsync(itemResult ->
        itemResult.after(singleOpenLoanFinder::findSingleOpenLoan));
  }

}
