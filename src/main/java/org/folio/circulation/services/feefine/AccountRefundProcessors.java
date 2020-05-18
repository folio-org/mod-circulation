package org.folio.circulation.services.feefine;

import static org.folio.circulation.services.feefine.FeeRefundProcessor.createLostItemFeeRefundProcessor;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;

import org.folio.circulation.domain.Account;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;

public final class AccountRefundProcessors {
  private static final FeeRefundProcessor LOST_ITEM_FEE_REFUND_PROCESSOR =
    createLostItemFeeRefundProcessor();

  private AccountRefundProcessors() {}

  public static Result<AccountRefundProcessor> getProcessor(Account account) {
    if (LOST_ITEM_FEE_REFUND_PROCESSOR.canHandleAccountRefund(account)) {
      return succeeded(LOST_ITEM_FEE_REFUND_PROCESSOR);
    }

    return failed(new ServerErrorFailure(
      "Can not find account refund processor for fee type " + account.getFeeFineType()));
  }
}
