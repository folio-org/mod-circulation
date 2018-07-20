package org.folio.circulation.domain.validation;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

import java.util.function.Function;

import static org.folio.circulation.support.HttpResult.succeeded;

public class AlreadyCheckedOutValidator {
  private final Function<String, ValidationErrorFailure> alreadyCheckedOutErrorFunction;

  public AlreadyCheckedOutValidator(
    Function<String, ValidationErrorFailure> alreadyCheckedOutErrorFunction) {

    this.alreadyCheckedOutErrorFunction = alreadyCheckedOutErrorFunction;
  }

  public HttpResult<LoanAndRelatedRecords> refuseWhenItemIsAlreadyCheckedOut(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.failWhen(
      records -> succeeded(records.getLoan().getItem().isCheckedOut()),
      r -> alreadyCheckedOutErrorFunction.apply("Item is already checked out"));
  }
}
