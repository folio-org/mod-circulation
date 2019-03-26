package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.succeeded;

import java.util.function.Function;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class ItemMissingValidator {
    private final Function<String, ValidationErrorFailure> itemMissingErrorFunction;

    public ItemMissingValidator(
      Function<String, ValidationErrorFailure> itemMissingErrorFunction) {

      this.itemMissingErrorFunction = itemMissingErrorFunction;
    }

    public Result<LoanAndRelatedRecords> refuseWhenItemIsMissing(
      Result<LoanAndRelatedRecords> result) {
      return result.failWhen(
        records -> succeeded(records.getLoan().getItem().isMissing()),
        loans -> {
          Item item = loans.getLoan().getItem();
          String message =
            String.format("%s (%s) (Barcode:%s) has the item status Missing and cannot be checked out",
                          item.getTitle(),
                          item.getMaterialType().getString("name"),
                          item.getBarcode());
          return itemMissingErrorFunction.apply(message);
        });
  }

}
