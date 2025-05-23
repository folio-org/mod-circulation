package org.folio.circulation.domain.validation;

import static java.lang.String.format;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.services.RequestQueueService;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class RequestedByAnotherPatronValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Function<String, ValidationErrorFailure> errorFunction;
  private final RequestQueueService requestQueueService;

  public RequestedByAnotherPatronValidator(Function<String, ValidationErrorFailure> errorFunction,
    RequestQueueService requestQueueService) {

    this.errorFunction = errorFunction;
    this.requestQueueService = requestQueueService;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenRequestedByAnotherPatron(
    Result<LoanAndRelatedRecords> result) {

    log.debug("refuseWhenRequestedByAnotherPatron:: parameters result: {}",
      () -> resultAsString(result));

    return result.failAfter(
      this::isRequestedByAnotherPatron,
      records -> requestedByAnotherPatronError(records.getLoan()));
  }

  private ValidationErrorFailure requestedByAnotherPatronError(Loan loan) {
    log.debug("requestedByAnotherPatronError:: parameters loan: {}", loan);

    Item item = loan.getItem();

    return errorFunction.apply(format(
      "%s (Barcode: %s) cannot be checked out to user %s" +
        " because it has been requested by another patron",
      item.getTitle(), item.getBarcode(), loan.getUser().getPersonalName()));

//    return result.after(l -> manualPatronBlocksValidator.validate(l)
//      .thenApply(r -> errorHandler.handleValidationResult(
//        r, manualPatronBlocksValidator.getErrorType(), result)));
  }

  private CompletableFuture<Result<Boolean>> isRequestedByAnotherPatron(
    LoanAndRelatedRecords records) {

    log.debug("isRequestedByAnotherPatron:: parameters records: {}", records);

    return requestQueueService.isItemRequestedByAnotherPatron(records.getRequestQueue(),
      records.getLoan().getUser(), records.getLoan().getItem());
  }
}
