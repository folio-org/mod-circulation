package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.HttpResult.succeeded;

import java.util.function.Function;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

public class ServicePointExistsValidator {
	private final Function<String, ValidationErrorFailure> servicePointAccessFunction;

  public ServicePointExistsValidator(
			Function<String, ValidationErrorFailure> servicePointAccessOutErrorFunction) {

		this.servicePointAccessFunction = servicePointAccessOutErrorFunction;
  }

	public HttpResult<LoanAndRelatedRecords> refuseWhenUserCannotAccessServicePoint(
    HttpResult<LoanAndRelatedRecords> result) {

		// TODO check if service point exists (CIRC-150)
    return result.failWhen(
				records -> succeeded(false),
				r -> servicePointAccessFunction
						.apply("So service point found by with the id:" + r.getLoan().getCheckoutServicePointId()));
  }
}
