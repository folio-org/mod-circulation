package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

import java.util.Objects;
import java.util.function.Function;

public class ItemDeclaredLostValidator {

  private final Function<String, ValidationErrorFailure> itemDeclaredLostErrorFunction;

  public ItemDeclaredLostValidator(
    Function<String, ValidationErrorFailure> itemDeclaredLostErrorFunction) {
    this.itemDeclaredLostErrorFunction = itemDeclaredLostErrorFunction;
  }

  public Result<LoanAndRelatedRecords> refuseWhenItemHasDeclaredLostStatus(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords.next(records -> {
      try {
        Item item = records.getLoan().getItem();
        if(Objects.equals(item.getStatus(), ItemStatus.DECLARED_LOST)){
          String message =
            String.format("%s (%s) (Barcode:%s) has the item status Declared lost and cannot be checked out",
              item.getTitle(),
              item.getMaterialType().getString("name"),
              item.getBarcode());

          return failed(itemDeclaredLostErrorFunction.apply(message));
        }
        return succeeded(records);
      } catch (Exception e) {
        return failedDueToServerError(e);
      }
    });
  }
}
