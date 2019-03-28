package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.succeeded;

import java.util.function.Function;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class ServicePointAccessValidator {
  private final Function<String, ValidationErrorFailure> servicePointAccessFunction;

  public ServicePointAccessValidator(
    Function<String, ValidationErrorFailure> servicePointAccessOutErrorFunction) {

    this.servicePointAccessFunction = servicePointAccessOutErrorFunction;
  }

  public Result<LoanAndRelatedRecords> refuseWhenUserCannotAccessServicePoint(
    Result<LoanAndRelatedRecords> result) {

    // TODO check user permissions (CIRC-150)
    return result.failWhen(
      records -> succeeded(false),
      r -> servicePointAccessFunction.apply("User must have access to the Service Point of Last Action."));
  }
}
