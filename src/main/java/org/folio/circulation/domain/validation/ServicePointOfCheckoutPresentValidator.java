package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.succeeded;

import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class ServicePointOfCheckoutPresentValidator {
  private final Function<String, ValidationErrorFailure> servicePointOfCheckoutPresentFuntion;

  public ServicePointOfCheckoutPresentValidator(
      Function<String, ValidationErrorFailure> servicePointOfCheckoutPresentErrorFunction) {

    this.servicePointOfCheckoutPresentFuntion = servicePointOfCheckoutPresentErrorFunction;
  }

  public Result<LoanAndRelatedRecords> refuseCheckOutWhenServicePointIsNotPresent(
    Result<LoanAndRelatedRecords> result) {

    return result.failWhen(
        records -> succeeded(StringUtils.isEmpty(records.getLoan().getCheckoutServicePointId())),
      r -> servicePointOfCheckoutPresentFuntion.apply("Check out must be performed at a service point"));
  }
}
