package org.folio.circulation.services.feefine;

import static org.folio.circulation.services.feefine.FeeTypeBasedRefundProcessor.createLostItemFeeRefundProcessor;
import static org.folio.circulation.support.Result.failed;

import java.util.Collections;
import java.util.List;

import org.folio.circulation.domain.Account;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;

public final class AccountRefundProcessors {
  private static final List<AccountRefundProcessor> PROCESSORS = getAvailableProcessors();

  private AccountRefundProcessors() {}

  public static Result<AccountRefundProcessor> getProcessor(Account account) {
    return PROCESSORS.stream()
      .filter(processor -> processor.canHandleAccountRefund(account))
      .findFirst()
      .map(Result::succeeded)
      .orElse(failed(new ServerErrorFailure("Can not find account refund processor for fee type "
      + account.getFeeFineType())));
  }

  private static List<AccountRefundProcessor> getAvailableProcessors() {
    return Collections.singletonList(createLostItemFeeRefundProcessor());
  }
}
