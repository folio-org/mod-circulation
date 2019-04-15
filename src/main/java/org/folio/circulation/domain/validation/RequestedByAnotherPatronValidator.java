package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.succeeded;

import java.util.function.Function;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.User;
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
    return errorFunction.apply(requestedByAnotherPatronMessage(
      loan.getItem(), loan.getUser()));
  }

  private String requestedByAnotherPatronMessage(Item item, User checkingOutUser) {
    return String.format("%s (Barcode: %s) cannot be checked out to user %s" +
        " because it is awaiting pickup by another patron",
      item.getTitle(), item.getBarcode(), checkingOutUser.getPersonalName());
  }

  private Result<Boolean> isRequestedByAnotherPatron(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return succeeded(loanAndRelatedRecords.getRequestQueue()
      .hasAwaitingPickupRequestForOtherPatron(loanAndRelatedRecords.getLoan().getUser()));
  }
}
