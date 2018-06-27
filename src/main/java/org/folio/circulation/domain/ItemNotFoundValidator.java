package org.folio.circulation.domain;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

import java.util.function.Function;

import static org.folio.circulation.support.HttpResult.failed;

public class ItemNotFoundValidator {
  private final Function<String, ValidationErrorFailure> itemNotFoundErrorFunction;

  public ItemNotFoundValidator(
    Function<String, ValidationErrorFailure> itemNotFoundErrorFunction) {

    this.itemNotFoundErrorFunction = itemNotFoundErrorFunction;
  }

  public HttpResult<LoanAndRelatedRecords> refuseWhenItemNotFound(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.next(loanAndRelatedRecords -> {
      if(loanAndRelatedRecords.getLoan().getItem().isNotFound()) {
        return failed(itemNotFoundErrorFunction.apply("item could not be found"));
      }
      else {
        return result;
      }
    });

  }
}
