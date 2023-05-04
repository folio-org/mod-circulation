package org.folio.circulation.domain.validation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.ValidationErrorFailure;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;

import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

public class AlreadyCheckedOutValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Function<String, ValidationErrorFailure> alreadyCheckedOutErrorFunction;

  public AlreadyCheckedOutValidator(
    Function<String, ValidationErrorFailure> alreadyCheckedOutErrorFunction) {

    this.alreadyCheckedOutErrorFunction = alreadyCheckedOutErrorFunction;
  }

  public Result<LoanAndRelatedRecords> refuseWhenItemIsAlreadyCheckedOut(
    Result<LoanAndRelatedRecords> result) {

    log.debug("refuseWhenItemIsAlreadyCheckedOut:: parameters result: {}",
      () -> resultAsString(result));

    return result.failWhen(
      records -> succeeded(records.getLoan().getItem().isCheckedOut()),
      r -> alreadyCheckedOutErrorFunction.apply("Item is already checked out"));
  }
}
