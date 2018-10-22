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

		// TODO check user permissions
    return result.failWhen(
				records -> succeeded(false),
				r -> ServicePointAccessFunction.apply("User must have access to the Service Point of Last Action."));
  }
}
