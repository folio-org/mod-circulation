package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.succeeded;

import java.util.function.Function;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class ServicePointExistsValidator {
  private final Function<String, ValidationErrorFailure> servicePointAccessFunction;

  public ServicePointExistsValidator(
    Function<String, ValidationErrorFailure> servicePointAccessOutErrorFunction) {

    this.servicePointAccessFunction = servicePointAccessOutErrorFunction;
  }

  public Result<LoanAndRelatedRecords> refuseWhenUserCannotAccessServicePoint(
    Result<LoanAndRelatedRecords> result) {

    // TODO check if service point exists (CIRC-150)
    return result.failWhen(
      records -> succeeded(false),
      r -> servicePointAccessFunction
        .apply("User cannot access Service Point:" + r.getLoan().getCheckoutServicePointId()));
  }
}
