package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.ItemStatus.INTELLECTUAL_ITEM;
import static org.folio.circulation.domain.representations.CheckInByBarcodeRequest.CLAIMED_RETURNED_RESOLUTION;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.support.ErrorCode.CHECKED_OUT_ITEM_IN_HOUSE_USE_REQUIRES_CONFIRMATION;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.override.OverridableBlockType;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.http.server.BlockOverrideError;
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

  public Result<CheckInContext> refuseWhenCheckedOutItemInHouseUseIsNotConfirmed(
    Result<CheckInContext> contextResult) {

    log.debug("refuseWhenCheckedOutItemInHouseUseIsNotConfirmed:: parameters contextResult: {}",
      () -> resultAsString(contextResult));

    return contextResult.failWhen(
      context -> succeeded(isCheckedOutItemInHouseUseNotConfirmed(context)),
      context -> new ValidationErrorFailure(new BlockOverrideError(
        "Item is checked out. Confirmation is required to check it in and register in-house use",
        Map.of(ITEM_BARCODE, String.valueOf(context.getCheckInRequestBarcode())),
        CHECKED_OUT_ITEM_IN_HOUSE_USE_REQUIRES_CONFIRMATION,
        OverridableBlockType.CHECKED_OUT_ITEM_IN_HOUSE_USE_OVERRIDE,
        OkapiPermissions.empty())));
  }

  private boolean isCheckedOutItemInHouseUseNotConfirmed(CheckInContext context) {
    final var request = context.getCheckInRequest();

    return request.isInHouseUse()
      && !request.isCheckedOutItemInHouseUseOverride()
      && context.getItem().isCheckedOut();
  }

  private boolean isClaimedReturnedNotResolved(CheckInContext context) {
    log.debug("isClaimedReturnedNotResolved:: parameters context: {}", context);
    var result = context.getItem().isClaimedReturned()
      && context.getCheckInRequest().getClaimedReturnedResolution() == null;
    log.info("isClaimedReturnedNotResolved:: result {}", result);
    return result;
  }
}
