package org.folio.circulation.domain.validation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.ValidationErrorFailure;

import java.lang.invoke.MethodHandles;
import java.util.function.Supplier;

import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

public class ItemNotFoundValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final Supplier<ValidationErrorFailure> itemNotFoundErrorFunction;

  public ItemNotFoundValidator(
    Supplier<ValidationErrorFailure> itemNotFoundErrorFunction) {

    this.itemNotFoundErrorFunction = itemNotFoundErrorFunction;
  }

  public Result<LoanAndRelatedRecords> refuseWhenItemNotFound(
    Result<LoanAndRelatedRecords> result) {

    log.debug("refuseWhenItemNotFound:: parameters result: {}", () -> resultAsString(result));

    return result.failWhen(
      records -> succeeded(records.getLoan().getItem().isNotFound()),
      r -> itemNotFoundErrorFunction.get());
  }
}
