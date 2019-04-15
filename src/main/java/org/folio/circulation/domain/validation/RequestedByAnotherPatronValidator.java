package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.succeeded;

import java.util.function.Function;

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
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords.next(loan -> {
      String itemTitle = loan.getLoan().getItem().getTitle();
      String itemBarcode = loan.getLoan().getItem().getBarcode();
      final User requestingUser = loan.getLoan().getUser();

      return loanAndRelatedRecords.failWhen(
        this::isRequestedByAnotherPatron,
        records -> errorFunction.apply(
        String.format("%s (Barcode: %s) cannot be checked out to user %s because it is awaiting pickup by another patron",
          itemTitle, itemBarcode, requestingUser.getPersonalName())));
    });
  }

  private Result<Boolean> isRequestedByAnotherPatron(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return succeeded(loanAndRelatedRecords.getRequestQueue()
      .hasAwaitingPickupRequestForOtherPatron(loanAndRelatedRecords.getLoan().getUser()));
  }
}
