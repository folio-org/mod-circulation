package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class ServicePointOfCheckoutPresentValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Function<String, ValidationErrorFailure> servicePointOfCheckoutPresentFuntion;

  public ServicePointOfCheckoutPresentValidator(
      Function<String, ValidationErrorFailure> servicePointOfCheckoutPresentErrorFunction) {

    this.servicePointOfCheckoutPresentFuntion = servicePointOfCheckoutPresentErrorFunction;
  }

  public Result<LoanAndRelatedRecords> refuseCheckOutWhenServicePointIsNotPresent(
    Result<LoanAndRelatedRecords> result) {

    log.debug("refuseCheckOutWhenServicePointIsNotPresent:: parameters records: {}",
      () -> resultAsString(result));

    return result.failWhen(
        records -> succeeded(StringUtils.isEmpty(records.getLoan().getCheckoutServicePointId())),
      r -> servicePointOfCheckoutPresentFuntion.apply("Check out must be performed at a service point"));
  }
}
