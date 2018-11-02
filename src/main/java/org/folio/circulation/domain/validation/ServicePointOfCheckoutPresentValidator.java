package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.HttpResult.succeeded;

import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

public class ServicePointOfCheckoutPresentValidator {
	private final Function<String, ValidationErrorFailure> servicePointOfCheckoutPresentFuntion;

  public ServicePointOfCheckoutPresentValidator(
      Function<String, ValidationErrorFailure> servicePointOfCheckoutPresentErrorFunction) {

    this.servicePointOfCheckoutPresentFuntion = servicePointOfCheckoutPresentErrorFunction;
  }

	public HttpResult<LoanAndRelatedRecords> refuseCheckOutWhenServicePointIsNotPresent(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.failWhen(
        records -> succeeded(StringUtils.isEmpty(records.getLoan().getCheckoutServicePointId())),
				r -> servicePointOfCheckoutPresentFuntion.apply("A Service Point must be specified."));
  }
}
