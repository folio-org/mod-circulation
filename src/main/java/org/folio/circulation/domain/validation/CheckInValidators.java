package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.ItemStatus.INTELLECTUAL_ITEM;
import static org.folio.circulation.domain.representations.CheckInByBarcodeRequest.CLAIMED_RETURNED_RESOLUTION;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.Item;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;

public final class CheckInValidators {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Function<Item, ValidationErrorFailure> itemStatusErrorFunction;

  public CheckInValidators(Function<Item, ValidationErrorFailure> itemStatusErrorFunction) {
    this.itemStatusErrorFunction = itemStatusErrorFunction;
  }

  public Result<CheckInContext> refuseWhenItemIsNotAllowedForCheckIn(
    Result<CheckInContext> checkInContextResult) {

    log.debug("refuseWhenItemIsNotAllowedForCheckIn:: parameters checkInContextResult: {}",
      checkInContextResult);

    return checkInContextResult.map(CheckInContext::getItem)
    .next(this::refuseWhenItemIsNotAllowedForCheckIn)
    .combine(checkInContextResult, (item, checkInContext) -> checkInContext.withItem(item));
  }

  public Result<Item> refuseWhenItemIsNotAllowedForCheckIn(Item item) {
    log.debug("refuseWhenItemIsNotAllowedForCheckIn:: parameters item: {}", item);

    return refuseWhenIsForIntellectualItem(Result.of(() -> item));
  }

  private Result<Item> refuseWhenIsForIntellectualItem(Result<Item> itemResult) {
    log.debug("refuseWhenIsForIntellectualItem:: parameters itemResult: {}",
      () -> resultAsString(itemResult));

    return itemResult.failWhen(
      item -> succeeded(item.isInStatus(INTELLECTUAL_ITEM)),
      itemStatusErrorFunction::apply);
  }

  public Result<CheckInContext> refuseWhenClaimedReturnedIsNotResolved(
    Result<CheckInContext> contextResult) {

    log.debug("refuseWhenClaimedReturnedIsNotResolved:: parameters contextResult: {}",
      () -> resultAsString(contextResult));

    return contextResult.failWhen(
      processRecords -> succeeded(isClaimedReturnedNotResolved(processRecords)),
      processRecords -> singleValidationError(
        "Item is claimed returned, a resolution is required to check in",
        CLAIMED_RETURNED_RESOLUTION, null));
  }

  private boolean isClaimedReturnedNotResolved(CheckInContext context) {
    log.debug("isClaimedReturnedNotResolved:: parameters context: {}", context);
    var result = context.getItem().isClaimedReturned()
      && context.getCheckInRequest().getClaimedReturnedResolution() == null;
    log.info("isClaimedReturnedNotResolved:: result {}", result);
    return result;
  }
}
