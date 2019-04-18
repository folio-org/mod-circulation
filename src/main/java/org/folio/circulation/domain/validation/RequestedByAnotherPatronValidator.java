package org.folio.circulation.domain.validation;

import static java.lang.String.format;
import static org.folio.circulation.support.Result.succeeded;

import java.util.function.Function;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class RequestedByAnotherPatronValidator {
  private final Function<String, ValidationErrorFailure> errorFunction;

  public RequestedByAnotherPatronValidator(
    Function<String, ValidationErrorFailure> errorFunction) {

    this.errorFunction = errorFunction;
  }

  public Result<LoanAndRelatedRecords> refuseWhenRequestedByAnotherPatron(
    Result<LoanAndRelatedRecords> result) {

    return result.failWhen(
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

  private Result<Boolean> isRequestedByAnotherPatron(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return succeeded(loanAndRelatedRecords.getRequestQueue()
      .isRequestedByOtherPatron(loanAndRelatedRecords.getLoan().getUser()));
  }
}
