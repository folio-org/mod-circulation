package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.PROXY_USER_BARCODE;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.USER_BARCODE;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;

public class InactiveUserValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Function<String, ValidationErrorFailure> inactiveUserErrorFunction;
  private final String inactiveUserMessage;
  private final String cannotDetermineMessage;
  private final Function<LoanAndRelatedRecords, User> userFunction;

  InactiveUserValidator(Function<LoanAndRelatedRecords, User> userFunction,
    String inactiveUserMessage, String cannotDetermineMessage,
    Function<String, ValidationErrorFailure> inactiveUserErrorFunction) {

    this.inactiveUserErrorFunction = inactiveUserErrorFunction;
    this.inactiveUserMessage = inactiveUserMessage;
    this.cannotDetermineMessage = cannotDetermineMessage;
    this.userFunction = userFunction;
  }

  public static InactiveUserValidator forProxy(String proxyUserBarcode) {
    log.debug("forProxy:: parameters proxyUserBarcode={}", proxyUserBarcode);

    return new InactiveUserValidator(LoanAndRelatedRecords::getProxy,
      "Cannot check out via inactive proxying user",
      "Cannot determine if proxying user is active or not",
      message -> singleValidationError(message,
        PROXY_USER_BARCODE, proxyUserBarcode));
  }

  public static InactiveUserValidator forUser(String userBarcode) {
    log.debug("forUser:: parameters userBarcode={}", userBarcode);

    return new InactiveUserValidator(records -> records.getLoan().getUser(),
      "Cannot check out to inactive user",
      "Cannot determine if user is active or not",
      message -> singleValidationError(message, USER_BARCODE, userBarcode));
  }

  public Result<LoanAndRelatedRecords> refuseWhenUserIsInactive(
    Result<LoanAndRelatedRecords> loanAndRelatedRecords) {

    log.debug("refuseWhenUserIsInactive:: parameters loanAndRelatedRecords={}",
      () -> resultAsString(loanAndRelatedRecords));

    return loanAndRelatedRecords.next(records -> {
        final User user = userFunction.apply(records);
        return refuseWhenUserIsInactive(user, records);
    });
  }

  Result<LoanAndRelatedRecords> refuseWhenUserIsInactive(
    User user, LoanAndRelatedRecords records) {

    log.debug("refuseWhenUserIsInactive:: parameters user={}, records={}", user, records);

    if (user == null) {
      log.info("refuseWhenUserIsInactive:: user is null");
      return succeeded(records);
    }
    else if (user.cannotDetermineStatus()) {
      log.info("refuseWhenUserIsInactive:: cannot determine status");
      return failed(inactiveUserErrorFunction.apply(cannotDetermineMessage));
    }
    if (user.isInactive()) {
      log.info("refuseWhenUserIsInactive:: user is inactive");
      return failed(inactiveUserErrorFunction.apply(inactiveUserMessage));
    } else {
      return succeeded(records);
    }
  }
}
