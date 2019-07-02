package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.PROXY_USER_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.USER_BARCODE;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.util.function.Function;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;

public class InactiveUserValidator {
  private final Function<String, ValidationErrorFailure> inactiveUserErrorFunction;
  private final String inactiveUserMessage;
  private final String cannotDetermineMessage;
  private final Function<LoanAndRelatedRecords, User> userFunction;

  InactiveUserValidator(
    Function<LoanAndRelatedRecords, User> userFunction,
    String inactiveUserMessage,
    String cannotDetermineMessage,
    Function<String, ValidationErrorFailure> inactiveUserErrorFunction) {

    this.inactiveUserErrorFunction = inactiveUserErrorFunction;
    this.inactiveUserMessage = inactiveUserMessage;
    this.cannotDetermineMessage = cannotDetermineMessage;
    this.userFunction = userFunction;
  }

  public static InactiveUserValidator forProxy(String proxyUserBarcode) {
    return new InactiveUserValidator(
      LoanAndRelatedRecords::getProxy,
      "Cannot check out via inactive proxying user",
      "Cannot determine if proxying user is active or not",
      message -> singleValidationError(message,
        PROXY_USER_BARCODE, proxyUserBarcode));
  }

  public static InactiveUserValidator forUser(String userBarcode) {
    return new InactiveUserValidator(
      records -> records.getLoan().getUser(),
      "Cannot check out to inactive user",
      "Cannot determine if user is active or not",
      message -> singleValidationError(message, USER_BARCODE, userBarcode));
  }

  public Result<LoanAndRelatedRecords> refuseWhenUserIsInactive(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords.next(records -> {
      try {
        final User user = userFunction.apply(records);

        return refuseWhenUserIsInactive(user, records);
      } catch (Exception e) {
        return failedDueToServerError(e);
      }
    });
  }

  Result<LoanAndRelatedRecords> refuseWhenUserIsInactive(
    User user, LoanAndRelatedRecords records) {

    if(user == null) {
      return succeeded(records);
    }
    else if (user.canDetermineStatus()) {
      return failed(inactiveUserErrorFunction.apply(cannotDetermineMessage));
    }
    if (user.isInactive()) {
      return failed(inactiveUserErrorFunction.apply(inactiveUserMessage));
    } else {
      return succeeded(records);
    }
  }
}
