package org.folio.circulation.domain.validation;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

import java.util.function.Supplier;

import static org.folio.circulation.support.Result.succeeded;

public class ItemNotFoundValidator {
  private final Supplier<ValidationErrorFailure> itemNotFoundErrorFunction;

  public ItemNotFoundValidator(
    Supplier<ValidationErrorFailure> itemNotFoundErrorFunction) {

    this.itemNotFoundErrorFunction = itemNotFoundErrorFunction;
  }

  public Result<LoanAndRelatedRecords> refuseWhenItemNotFound(
    Result<LoanAndRelatedRecords> result) {

    return result.failWhen(
      records -> succeeded(records.getLoan().getItem().isNotFound()),
      r -> itemNotFoundErrorFunction.get());
  }
}
