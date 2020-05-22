package org.folio.circulation.services.feefine;

import org.folio.circulation.domain.Account;

public interface AccountRefundProcessor {
  boolean canHandleAccountRefund(Account account);

  void onHasTransferAmount(AccountRefundContext context);

  void onTransferAmountRefundActionSaved(AccountRefundContext context);

  void onHasPaidAmount(AccountRefundContext context);

  void onPaidAmountRefundActionSaved(AccountRefundContext context);

  void onHasRemainingAmount(AccountRefundContext context);

  void onRemainingAmountActionSaved(AccountRefundContext context);
}
