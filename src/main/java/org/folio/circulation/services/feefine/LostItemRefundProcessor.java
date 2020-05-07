package org.folio.circulation.services.feefine;

import static org.folio.circulation.domain.representations.AccountPaymentStatus.CANCELLED_ITEM_RETURNED;

import java.util.Arrays;
import java.util.List;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.representations.AccountPaymentStatus;

public final class LostItemRefundProcessor extends BaseAccountRefundProcessor {
  private static final List<String> SUPPORTED_FEES = Arrays.asList(
    FeeFine.LOST_ITEM_FEE_TYPE, FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE);

  @Override
  public final boolean canHandleAccountRefund(Account account) {
    return SUPPORTED_FEES.contains(account.getFeeFineType());
  }

  @Override
  protected final String getRefundReason(Account account) {
    return "Lost item found";
  }

  @Override
  protected final AccountPaymentStatus getClosedPaymentStatus() {
    return CANCELLED_ITEM_RETURNED;
  }
}
