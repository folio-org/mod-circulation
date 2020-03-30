package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.ItemStatus.CLAIMED_RETURNED;
import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.domain.ItemStatus.MISSING;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_ID;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;

import java.util.function.Function;

public class ItemStatusValidator {

  private final Function<String, ValidationErrorFailure> itemStatusErrorFunction;

  public ItemStatusValidator(
    Function<String, ValidationErrorFailure> itemStatusErrorFunction) {
    this.itemStatusErrorFunction = itemStatusErrorFunction;
  }

  private Result<LoanAndRelatedRecords> refuseWhenItemIs(
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

  public Result<LoanAndRelatedRecords> refuseWhenItemIsMissing(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords
      .next(p -> refuseWhenItemIs(loanAndRelatedRecords, MISSING))
      .next(p -> refuseWhenItemIs(loanAndRelatedRecords, DECLARED_LOST));
  }

  public Result<LoanAndRelatedRecords> refuseWhenItemIsNotAllowedForCheckOut(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords
      .next(p -> refuseWhenItemIs(loanAndRelatedRecords, DECLARED_LOST))
      .next(p -> refuseWhenItemIs(loanAndRelatedRecords, CLAIMED_RETURNED));
  }

  public static Result<LoanAndRelatedRecords> refuseWhenItemIsDeclaredLost(
    Result<LoanAndRelatedRecords> loanResult) {

    return loanResult.failWhen(
      l -> succeeded(l.getLoan().getItem().isInStatus(DECLARED_LOST)),
      l -> singleValidationError(new ValidationError("item is Declared lost",
         ITEM_ID, l.getLoan().getItem().getItemId())));
  }

  public static Result<LoanAndRelatedRecords> refuseWhenItemIsClaimedReturned(
    Result<LoanAndRelatedRecords> loanResult) {

    return loanResult.failWhen(
      l -> succeeded(l.getLoan().getItem().isInStatus(CLAIMED_RETURNED)),
      l -> singleValidationError(new ValidationError("item is Claimed returned",
         ITEM_ID, l.getLoan().getItem().getItemId())));
  }
}
