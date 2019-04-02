package org.folio.circulation.domain.validation;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

import java.util.function.Function;

import static org.folio.circulation.support.Result.succeeded;

public class AlreadyCheckedOutValidator {
  private final Function<String, ValidationErrorFailure> alreadyCheckedOutErrorFunction;

  public AlreadyCheckedOutValidator(
    Function<String, ValidationErrorFailure> alreadyCheckedOutErrorFunction) {

    this.alreadyCheckedOutErrorFunction = alreadyCheckedOutErrorFunction;
  }

  public Result<LoanAndRelatedRecords> refuseWhenItemIsAlreadyCheckedOut(
    Result<LoanAndRelatedRecords> result) {

    return result.failWhen(
      records -> succeeded(records.getLoan().getItem().isCheckedOut()),
      r -> alreadyCheckedOutErrorFunction.apply("Item is already checked out"));
  }
}
