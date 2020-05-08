package org.folio.circulation.services.feefine;

import static org.folio.circulation.domain.FeeAmount.zeroFeeAmount;
import static org.folio.circulation.domain.representations.AccountPaymentStatus.CREDITED_FULLY;
import static org.folio.circulation.domain.representations.AccountPaymentStatus.REFUNDED_FULLY;
import static org.folio.circulation.domain.representations.StoredFeeFineAction.StoredFeeFineActionBuilder;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeAmount;
import org.folio.circulation.domain.representations.AccountPaymentStatus;
import org.folio.circulation.domain.representations.StoredFeeFineAction;

abstract class BaseAccountRefundProcessor implements AccountRefundProcessor {

  @Override
  public void onHasTransferAmount(AccountRefundContext context) {
    final Account account = context.getAccount();
    final String refundReason = getRefundReason(account);
    final String transferRefundTransactionInfo = getTransferRefundTransactionInfo(account);

    context.subtractRemainingAmount(account.getTransferredAmount());

    context.addActions(buildCreditAction(refundReason, account.getTransferredAmount(),
      transferRefundTransactionInfo, context));

    context.addRemainingAmount(account.getTransferredAmount());

    context.addActions(buildRefundAction(refundReason, account.getTransferredAmount(),
      transferRefundTransactionInfo, context));
  }

  @Override
  public void onTransferAmountRefundActionSaved(AccountRefundContext context) {
    context.setPaymentStatus(REFUNDED_FULLY);
  }

  @Override
  public void onHasPaidAmount(AccountRefundContext context) {
    final Account account = context.getAccount();
    final String refundReason = getRefundReason(account);
    final String paymentRefundTransactionInfo = "Refund to patron";

    context.subtractRemainingAmount(account.getPaidAmount());

    context.addActions(buildCreditAction(refundReason, account.getPaidAmount(),
      paymentRefundTransactionInfo, context));

    context.addRemainingAmount(account.getPaidAmount());

    context.addActions(buildRefundAction(refundReason, account.getPaidAmount(),
      paymentRefundTransactionInfo, context));
  }

  @Override
  public void onPaidAmountRefundActionSaved(AccountRefundContext context) {
    context.setPaymentStatus(REFUNDED_FULLY);
  }

  @Override
  public void onHasRemainingAmount(AccountRefundContext context) {
    context.addActions(populateCommonAttributes(context)
      .withAction(getClosedPaymentStatus())
      .withBalance(zeroFeeAmount())
      .withAmount(context.getAccount().getRemaining())
      .build());
  }

  @Override
  public void onRemainingAmountActionSaved(AccountRefundContext context) {
    context.closeAccount(getClosedPaymentStatus());
  }

  private StoredFeeFineAction buildCreditAction(String refundReason,
    FeeAmount actionAmount, String transactionInfo, AccountRefundContext context) {

    return populateCommonAttributes(context)
      .withAction(CREDITED_FULLY)
      .withAmount(actionAmount)
      .withPaymentMethod(refundReason)
      .withTransactionInformation(transactionInfo)
      .build();
  }

  private StoredFeeFineAction buildRefundAction(String refundReason,
    FeeAmount actionAmount, String transactionInfo, AccountRefundContext context) {

    return populateCommonAttributes(context)
      .withAction(REFUNDED_FULLY)
      .withAmount(actionAmount)
      .withPaymentMethod(refundReason)
      .withTransactionInformation(transactionInfo)
      .build();
  }

  private StoredFeeFineActionBuilder populateCommonAttributes(AccountRefundContext context) {
    return StoredFeeFineAction.builder()
      .useAccount(context.getAccount())
      .withCreatedAt(context.getServicePointName())
      .withCreatedBy(context.getUser())
      .withBalance(context.getAccount().getRemaining());
  }

  protected abstract String getRefundReason(Account account);

  protected abstract AccountPaymentStatus getClosedPaymentStatus();

  private String getTransferRefundTransactionInfo(Account account) {
    return "Refund to " + account.getTransferAccountName();
  }
}
