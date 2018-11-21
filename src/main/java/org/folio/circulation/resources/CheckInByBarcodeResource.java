package org.folio.circulation.resources;

import java.util.function.BiFunction;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanCheckinService;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.domain.representations.LoanResponse;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
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
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final LoanCheckinService loanCheckinService = new LoanCheckinService();

    final UpdateItem updateItem = new UpdateItem(clients);
    final UpdateRequestQueue requestQueueUpdate = UpdateRequestQueue.using(clients);

    // TODO: Validation check for same user should be in the domain service

    final HttpResult<CheckInByBarcodeRequest> checkInRequestResult
      = CheckInByBarcodeRequest.from(routingContext.getBodyAsJson());

    checkInRequestResult
      .after(loanRepository::findOpenLoanByBarcode)
      .thenApply(loanResult -> loanResult.combineToResult(checkInRequestResult,
        loanCheckinService::checkin))
      .thenComposeAsync(result -> result.after(loanRepository::updateLoan))
      .thenComposeAsync(loanResult -> loanResult.combineAfter(
        loan -> requestQueueRepository.get(loan.getItemId()), mapToRelatedRecords()))
      .thenComposeAsync(result -> result.after(requestQueueUpdate::onCheckIn))
      .thenComposeAsync(result -> result.after(updateItem::onLoanUpdate))
      .thenComposeAsync(result -> result.after(loanRepository::updateLoan))
      .thenApply(result -> result.map(LoanAndRelatedRecords::getLoan))
      .thenApply(result -> result.map(loanRepresentation::extendedLoan))
      .thenApply(LoanResponse::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private BiFunction<Loan, RequestQueue, LoanAndRelatedRecords> mapToRelatedRecords() {
    return (loan, requestQueue) -> new LoanAndRelatedRecords(loan).withRequestQueue(requestQueue);
  }
}
