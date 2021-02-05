package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.resources.RenewalValidator.loanPolicyValidationError;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.HashMap;
import java.util.Map;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.results.Result;

public class LoanPolicyValidator {
  public Result<LoanAndRelatedRecords> refuseWhenItemIsNotLoanable(
    LoanAndRelatedRecords relatedRecords) {

    final Loan loan = relatedRecords.getLoan();
    final LoanPolicy loanPolicy = loan.getLoanPolicy();

    if (loanPolicy.isNotLoanable()) {
      String itemBarcode = loan.getItem().getBarcode();

      Map<String, String> parameters = new HashMap<>();
      parameters.put(ITEM_BARCODE, itemBarcode);
      return failed(singleValidationError(
        loanPolicyValidationError(loanPolicy,
          "Item is not loanable", parameters)));
    }
    return succeeded(relatedRecords);
  }
}
