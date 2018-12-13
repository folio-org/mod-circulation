package org.folio.circulation.domain.validation;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.support.HttpResult;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import org.folio.circulation.support.ValidationErrorFailure;

public class ServicePointLoanLocationValidator {
  public HttpResult<LoanAndRelatedRecords> checkServicePointLoanLocation(
      HttpResult<LoanAndRelatedRecords> larr) {
    return larr.next(this::refuseInvalidLoanServicePoints);
  }
  
  private HttpResult<LoanAndRelatedRecords> refuseInvalidLoanServicePoints(LoanAndRelatedRecords larr) {
    Loan loan = null;
    
    if(larr == null) {
      return succeeded(larr);
    }
    
    loan = larr.getLoan();
    
    if(loan == null) {
      return succeeded(larr);
    }
    
    if(loan.getCheckInServicePointId() != null && loan.getCheckinServicePoint() == null) {
      return failed(ValidationErrorFailure.failure("Check In Service Point does not exist",
          "checkinServicePointId", loan.getCheckInServicePointId()));
    }
    
    if(loan.getCheckoutServicePointId() != null && loan.getCheckoutServicePoint() == null) {
      return failed(ValidationErrorFailure.failure("Check Out Service Point does not exist",
          "checkoutServicePointId", loan.getCheckoutServicePointId()));
    }
    
    return succeeded(larr);
  }
}
