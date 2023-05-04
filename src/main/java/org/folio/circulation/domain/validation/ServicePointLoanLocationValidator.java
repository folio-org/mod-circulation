package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.results.Result;

public class ServicePointLoanLocationValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public Result<LoanAndRelatedRecords> checkServicePointLoanLocation(
      Result<LoanAndRelatedRecords> larr) {
    return larr.next(this::refuseInvalidLoanServicePoints);
  }

  private Result<LoanAndRelatedRecords> refuseInvalidLoanServicePoints(LoanAndRelatedRecords records) {
    log.debug("refuseInvalidLoanServicePoints:: parameters records: {}", records);

    Loan loan;

    if (records == null) {
      log.info("refuseInvalidLoanServicePoints:: records is null");
      return succeeded(records);
    }

    loan = records.getLoan();

    if (loan == null) {
      log.info("refuseInvalidLoanServicePoints:: loan is null");
      return succeeded(records);
    }

    if (loan.getCheckInServicePointId() != null && loan.getCheckinServicePoint() == null) {
      log.warn("refuseInvalidLoanServicePoints:: Check In Service Point does not exist");
      return failedValidation("Check In Service Point does not exist",
          "checkinServicePointId", loan.getCheckInServicePointId());
    }

    if (loan.getCheckoutServicePointId() != null && loan.getCheckoutServicePoint() == null) {
      log.warn("refuseInvalidLoanServicePoints:: Check Out Service Point does not exist");
      return failedValidation("Check Out Service Point does not exist",
          "checkoutServicePointId", loan.getCheckoutServicePointId());
    }

    return succeeded(records);
  }
}
