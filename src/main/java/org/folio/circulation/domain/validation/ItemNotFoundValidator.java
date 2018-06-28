package org.folio.circulation.domain.validation;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

import java.util.function.Supplier;

import static org.folio.circulation.support.HttpResult.failed;

public class ItemNotFoundValidator {
  private final Supplier<ValidationErrorFailure> itemNotFoundErrorFunction;

  public ItemNotFoundValidator(
    Supplier<ValidationErrorFailure> itemNotFoundErrorFunction) {

    this.itemNotFoundErrorFunction = itemNotFoundErrorFunction;
  }

  public HttpResult<LoanAndRelatedRecords> refuseWhenItemNotFound(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.next(loanAndRelatedRecords -> {
      if(loanAndRelatedRecords.getLoan().getItem().isNotFound()) {
        return failed(itemNotFoundErrorFunction.get());
      }
      else {
        return result;
      }
    });

  }
}
