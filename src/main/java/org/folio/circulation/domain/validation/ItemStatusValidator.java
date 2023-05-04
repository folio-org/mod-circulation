package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.ItemStatus.AGED_TO_LOST;
import static org.folio.circulation.domain.ItemStatus.CLAIMED_RETURNED;
import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.domain.ItemStatus.INTELLECTUAL_ITEM;
import static org.folio.circulation.domain.ItemStatus.MISSING;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class ItemStatusValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Function<Item, ValidationErrorFailure> itemStatusErrorFunction;

  public ItemStatusValidator(Function<Item, ValidationErrorFailure> itemStatusErrorFunction) {
    this.itemStatusErrorFunction = itemStatusErrorFunction;
  }

  private Result<LoanAndRelatedRecords> refuseWhenItemIs(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords, ItemStatus status) {

    log.debug("refuseWhenItemIs:: parameters loanAndRelatedRecords: {}, status: {}",
      () -> resultAsString(loanAndRelatedRecords), () -> status);

    return loanAndRelatedRecords.failWhen(
      records -> succeeded(records.getLoan().getItem().isInStatus(status)),
      loans -> itemStatusErrorFunction.apply(loans.getLoan().getItem()));
  }

  public Result<LoanAndRelatedRecords> refuseWhenItemIsMissing(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    log.debug("refuseWhenItemIsMissing:: parameters loanAndRelatedRecords: {}",
      () -> resultAsString(loanAndRelatedRecords));

    return loanAndRelatedRecords
      .next(p -> refuseWhenItemIs(loanAndRelatedRecords, MISSING))
      .next(p -> refuseWhenItemIs(loanAndRelatedRecords, DECLARED_LOST));
  }

  public Result<LoanAndRelatedRecords> refuseWhenItemIsNotAllowedForCheckOut(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    log.debug("refuseWhenItemIsNotAllowedForCheckOut:: parameters loanAndRelatedRecords: {}",
      () -> resultAsString(loanAndRelatedRecords));

    return loanAndRelatedRecords
      .next(p -> refuseWhenItemIs(loanAndRelatedRecords, DECLARED_LOST))
      .next(p -> refuseWhenItemIs(loanAndRelatedRecords, CLAIMED_RETURNED))
      .next(p -> refuseWhenItemIs(loanAndRelatedRecords, INTELLECTUAL_ITEM))
      .next(p -> refuseWhenItemIs(loanAndRelatedRecords, AGED_TO_LOST));
  }

  public Result<LoanAndRelatedRecords> refuseWhenItemStatusDoesNotAllowDueDateChange(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    log.debug("refuseWhenItemStatusDoesNotAllowDueDateChange:: parameters loanAndRelatedRecords: {}",
      () -> resultAsString(loanAndRelatedRecords));

    return loanAndRelatedRecords
      .next(p -> refuseWhenItemIs(loanAndRelatedRecords, DECLARED_LOST))
      .next(p -> refuseWhenItemIs(loanAndRelatedRecords, CLAIMED_RETURNED))
      .next(p -> refuseWhenItemIs(loanAndRelatedRecords, AGED_TO_LOST));
  }
}
