package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.representations.LoanProperties.ITEM_ID;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Result;

public class HoldingsValidator {

  public static Result<LoanAndRelatedRecords> refuseWhenHoldingDoesNotExist(
    Result<LoanAndRelatedRecords> result) {

    return result.next(loan -> {
      if (loan.getLoan().getItem().doesNotHaveHolding()) {
        return failedValidation("Holding does not exist",
          ITEM_ID, loan.getLoan().getItemId());
      } else {
        return result;
      }
    });
  }
}
