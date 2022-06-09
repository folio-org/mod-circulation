package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.ItemStatus.INTELLECTUAL_ITEM;
import static org.folio.circulation.domain.representations.CheckInByBarcodeRequest.CLAIMED_RETURNED_RESOLUTION;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.function.Function;

import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.Item;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;

public final class CheckInValidators {
  private final Function<Item, ValidationErrorFailure> itemStatusErrorFunction;

  public CheckInValidators(Function<Item, ValidationErrorFailure> itemStatusErrorFunction) {
    this.itemStatusErrorFunction = itemStatusErrorFunction;
  }

  public Result<CheckInContext> refuseWhenItemIsNotAllowedForCheckIn(Result<CheckInContext> checkInContextResult) {
    return checkInContextResult.map(CheckInContext::getItem)
    .next(this::refuseWhenItemIsNotAllowedForCheckIn)
    .combine(checkInContextResult, (item, checkInContext) -> checkInContext.withItem(item));
  }

  public Result<Item> refuseWhenItemIsNotAllowedForCheckIn(Item item) {
    return refuseWhenIsForIntellectualItem(Result.of(() -> item));
  }

  private Result<Item> refuseWhenIsForIntellectualItem(Result<Item> itemResult) {
    return itemResult.failWhen(
      item -> succeeded(item.isInStatus(INTELLECTUAL_ITEM)),
      itemStatusErrorFunction::apply);
  }

  public Result<CheckInContext> refuseWhenClaimedReturnedIsNotResolved(
    Result<CheckInContext> contextResult) {

    return contextResult.failWhen(
      processRecords -> succeeded(isClaimedReturnedNotResolved(processRecords)),
      processRecords -> singleValidationError(
        "Item is claimed returned, a resolution is required to check in",
        CLAIMED_RETURNED_RESOLUTION, null));
  }

  private boolean isClaimedReturnedNotResolved(CheckInContext context) {
    return context.getItem().isClaimedReturned()
      && context.getCheckInRequest().getClaimedReturnedResolution() == null;
  }
}
