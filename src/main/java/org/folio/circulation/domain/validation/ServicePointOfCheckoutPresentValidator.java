package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.HttpResult.succeeded;

import java.util.function.Function;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

public class ServicePointOfCheckoutPresentValidator {
	private final Function<String, ValidationErrorFailure> servicePointOfCheckoutPresentFuntion;

  public ServicePointOfCheckoutPresentValidator(
			Function<String, ValidationErrorFailure> servicePointOfCheckoutPresentErrorFuntion) {

		this.servicePointOfCheckoutPresentFuntion = servicePointOfCheckoutPresentErrorFuntion;
  }

	public HttpResult<LoanAndRelatedRecords> refuseServicePointOfCheckoutIsNotPresent(
    HttpResult<LoanAndRelatedRecords> result) {
		
    return result.failWhen(
				records -> succeeded(records.getLoan().getServicePointOfCheckout() == null),
				r -> servicePointOfCheckoutPresentFuntion.apply("A Service Point must be specified."));
  }
}
