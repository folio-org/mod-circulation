package org.folio.circulation.domain.validation;

import static java.lang.String.format;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.services.RequestQueueService;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class RequestedByAnotherPatronValidator {
  private final Function<String, ValidationErrorFailure> errorFunction;
  private final RequestQueueService requestQueueService;

  public RequestedByAnotherPatronValidator(Function<String, ValidationErrorFailure> errorFunction,
    RequestQueueService requestQueueService) {

    this.errorFunction = errorFunction;
    this.requestQueueService = requestQueueService;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenRequestedByAnotherPatron(
    Result<LoanAndRelatedRecords> result) {

    return result.failAfter(
      this::isRequestedByAnotherPatron,
      records -> requestedByAnotherPatronError(records.getLoan()));
  }

  private ValidationErrorFailure requestedByAnotherPatronError(Loan loan) {
    Item item = loan.getItem();

    return errorFunction.apply(format(
      "%s (Barcode: %s) cannot be checked out to user %s" +
        " because it has been requested by another patron",
      item.getTitle(), item.getBarcode(), loan.getUser().getPersonalName()));
  }

  private CompletableFuture<Result<Boolean>> isRequestedByAnotherPatron(
    LoanAndRelatedRecords records) {

    return requestQueueService.isItemRequestedByAnotherPatron(records.getRequestQueue(),
      records.getLoan().getUser(), records.getLoan().getItem());
  }
}
