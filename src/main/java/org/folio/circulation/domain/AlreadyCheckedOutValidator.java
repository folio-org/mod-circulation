package org.folio.circulation.domain;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

import java.util.function.Function;

import static org.folio.circulation.support.HttpResult.failure;

public class AlreadyCheckedOutValidator {
  private final Function<String, ValidationErrorFailure> alreadyCheckedOutErrorFunction;

  public AlreadyCheckedOutValidator(
    Function<String, ValidationErrorFailure> alreadyCheckedOutErrorFunction) {

    this.alreadyCheckedOutErrorFunction = alreadyCheckedOutErrorFunction;
  }

  public HttpResult<LoanAndRelatedRecords> refuseWhenItemIsAlreadyCheckedOut(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords.next(records -> {
      if(records.getLoan().getItem().isCheckedOut()) {
        return failure(alreadyCheckedOutErrorFunction.apply("Item is already checked out"));
      }
      else {
        return loanAndRelatedRecords;
      }
    });
  }
}
