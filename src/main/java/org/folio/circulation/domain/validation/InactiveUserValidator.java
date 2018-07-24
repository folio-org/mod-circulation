package org.folio.circulation.domain.validation;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;

import java.util.function.Function;

import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.PROXY_USER_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.USER_BARCODE;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

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
      message -> failure(message, PROXY_USER_BARCODE, proxyUserBarcode));
  }

  public static InactiveUserValidator forUser(String userBarcode) {
    return new InactiveUserValidator(
      records -> records.getLoan().getUser(),
      "Cannot check out to inactive user",
      "Cannot determine if user is active or not",
      message -> failure(message, USER_BARCODE, userBarcode));
  }

  public HttpResult<LoanAndRelatedRecords> refuseWhenUserIsInactive(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords.next(records -> {
      try {
        final User user = userFunction.apply(records);

        return refuseWhenUserIsInactive(user, records);
      } catch (Exception e) {
        return failed(new ServerErrorFailure(e));
      }
    });
  }

  HttpResult<LoanAndRelatedRecords> refuseWhenUserIsInactive(
    User user, LoanAndRelatedRecords records) {

    if(user == null) {
      return succeeded(records);
    }
    else if (user.canDetermineStatus()) {
      return failed(inactiveUserErrorFunction.apply(
        cannotDetermineMessage));
    }
    if (user.isInactive()) {
      return failed(inactiveUserErrorFunction.apply(
        inactiveUserMessage));
    } else {
      return succeeded(records);
    }
  }
}
