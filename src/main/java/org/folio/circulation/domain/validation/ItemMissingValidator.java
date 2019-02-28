package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.HttpResult.succeeded;

import java.util.function.Function;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

public class ItemMissingValidator {
    private final Function<String, ValidationErrorFailure> itemMissingErrorFunction;

    public ItemMissingValidator(
      Function<String, ValidationErrorFailure> itemMissingErrorFunction) {

      this.itemMissingErrorFunction = itemMissingErrorFunction;
    }

    public HttpResult<LoanAndRelatedRecords> refuseWhenItemIsMissing(
      HttpResult<LoanAndRelatedRecords> result) {

      return result.failWhen(
        records -> succeeded(records.getLoan().getItem().isMissing()),
        x -> itemMissingErrorFunction.apply("Item is missing"));
  }

}
