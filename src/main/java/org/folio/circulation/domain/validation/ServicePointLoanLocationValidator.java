package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Result;

public class ServicePointLoanLocationValidator {
  public Result<LoanAndRelatedRecords> checkServicePointLoanLocation(
      Result<LoanAndRelatedRecords> larr) {
    return larr.next(this::refuseInvalidLoanServicePoints);
  }
  
  private Result<LoanAndRelatedRecords> refuseInvalidLoanServicePoints(LoanAndRelatedRecords larr) {
    Loan loan = null;
    
    if(larr == null) {
      return succeeded(larr);
    }
    
    loan = larr.getLoan();
    
    if(loan == null) {
      return succeeded(larr);
    }
    
    if(loan.getCheckInServicePointId() != null && loan.getCheckinServicePoint() == null) {
      return failedValidation("Check In Service Point does not exist",
          "checkinServicePointId", loan.getCheckInServicePointId());
    }
    
    if(loan.getCheckoutServicePointId() != null && loan.getCheckoutServicePoint() == null) {
      return failedValidation("Check Out Service Point does not exist",
          "checkoutServicePointId", loan.getCheckoutServicePointId());
    }
    
    return succeeded(larr);
  }
}
