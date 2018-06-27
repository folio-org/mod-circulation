package org.folio.circulation.domain;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;

import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.PROXY_USER_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.USER_BARCODE;
import static org.folio.circulation.support.HttpResult.failure;
import static org.folio.circulation.support.HttpResult.success;

public class InactiveUserValidator {
  public HttpResult<LoanAndRelatedRecords> refuseWhenRequestingUserIsInactive(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords.next(loan -> {
      try {
        final User requestingUser = loan.getLoan().getUser();

        if (requestingUser.canDetermineStatus()) {
          return failure(ValidationErrorFailure.failure(
            "Cannot determine if user is active or not",
            USER_BARCODE, requestingUser.getBarcode()));
        }
        if (requestingUser.isInactive()) {
          return failure(ValidationErrorFailure.failure(
            "Cannot check out to inactive user",
            USER_BARCODE, requestingUser.getBarcode()));
        } else {
          return success(loan);
        }
      } catch (Exception e) {
        return failure(new ServerErrorFailure(e));
      }
    });
  }

  public HttpResult<LoanAndRelatedRecords> refuseWhenProxyingUserIsInactive(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords.next(loan -> {
      final User proxyingUser = loan.getProxyingUser();

      if(proxyingUser == null) {
        return loanAndRelatedRecords;
      }
      else if (proxyingUser.canDetermineStatus()) {
        return failure(ValidationErrorFailure.failure(
          "Cannot determine if proxying user is active or not",
          PROXY_USER_BARCODE, proxyingUser.getBarcode()));
      }
      else if(proxyingUser.isInactive()) {
        return failure(ValidationErrorFailure.failure(
          "Cannot check out via inactive proxying user",
          PROXY_USER_BARCODE, proxyingUser.getBarcode()));
      }
      else {
        return loanAndRelatedRecords;
      }
    });
  }
}
