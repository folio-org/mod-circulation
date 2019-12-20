package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.succeeded;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

import java.util.function.Function;

public class ItemStatusValidator {

  private final Function<String, ValidationErrorFailure> itemStatusErrorFunction;

  public ItemStatusValidator(
    Function<String, ValidationErrorFailure> itemStatusErrorFunction) {
    this.itemStatusErrorFunction = itemStatusErrorFunction;
  }

  private Result<LoanAndRelatedRecords> checkAndRefuseItem(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords, ItemStatus status) {

    return loanAndRelatedRecords.failWhen(
      records -> succeeded(records.getLoan().getItem().isInStatus(status)),
      loans -> {
        Item item = loans.getLoan().getItem();
        String message =
          String.format("%s (%s) (Barcode:%s) has the item status %s and cannot be checked out",
            item.getTitle(),
            item.getMaterialTypeName(),
            item.getBarcode(),
            item.getStatusName());
        return itemStatusErrorFunction.apply(message);
      });
  }

  public Result<LoanAndRelatedRecords> refuseWhenItemStatusIsInvalid(Result<LoanAndRelatedRecords> loanAndRelatedRecords) {
    return loanAndRelatedRecords
      .next(p -> checkAndRefuseItem(loanAndRelatedRecords, ItemStatus.MISSING))
      .next(p -> checkAndRefuseItem(loanAndRelatedRecords, ItemStatus.DECLARED_LOST));
  }
}
