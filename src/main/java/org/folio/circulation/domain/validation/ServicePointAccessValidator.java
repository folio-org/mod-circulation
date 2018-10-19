package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.HttpResult.succeeded;

import java.util.function.Function;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

public class ServicePointAccessValidator {
	private final Function<String, ValidationErrorFailure> ServicePointAccessFunction;

  public ServicePointAccessValidator(
			Function<String, ValidationErrorFailure> servicePointAccessOutErrorFunction) {

		this.ServicePointAccessFunction = servicePointAccessOutErrorFunction;
  }

	public HttpResult<LoanAndRelatedRecords> refuseWhenUserCannotAccessServicePoint(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.failWhen(
				records -> succeeded(records.getLoan().getServicePointOfCheckout() == null),
				r -> ServicePointAccessFunction.apply("User must have access to the Service Point of Last Action."));
  }
}
