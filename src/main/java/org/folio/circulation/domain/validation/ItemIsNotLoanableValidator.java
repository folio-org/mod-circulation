package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.HttpResult.succeeded;

import java.util.function.Supplier;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

public class ItemIsNotLoanableValidator {

  private final Supplier<ValidationErrorFailure> itemIsNotLoanableFunction;

  public ItemIsNotLoanableValidator(
    Supplier<ValidationErrorFailure> itemNotFoundErrorFunction) {
    this.itemIsNotLoanableFunction = itemNotFoundErrorFunction;
  }

  public HttpResult<LoanAndRelatedRecords> refuseWhenItemIsNotLoanable(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.failWhen(
      records -> succeeded(records.getLoanPolicy().isNotLoanable()),
      r -> itemIsNotLoanableFunction.get());
  }
}
