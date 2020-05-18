package org.folio.circulation.services.feefine;

import static java.util.Arrays.asList;
import static org.folio.circulation.domain.AccountRefundReason.LOST_ITEM_FOUND;
import static org.folio.circulation.domain.FeeAmount.zeroFeeAmount;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.domain.representations.AccountPaymentStatus.CANCELLED_ITEM_RETURNED;
import static org.folio.circulation.domain.representations.AccountPaymentStatus.CREDITED_FULLY;
import static org.folio.circulation.domain.representations.AccountPaymentStatus.REFUNDED_FULLY;
import static org.folio.circulation.domain.representations.StoredFeeFineAction.StoredFeeFineActionBuilder;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.AccountRefundReason;
import org.folio.circulation.domain.FeeAmount;
import org.folio.circulation.domain.representations.AccountPaymentStatus;
import org.folio.circulation.domain.representations.StoredFeeFineAction;

public class FeeRefundProcessor implements AccountRefundProcessor {
  private final AccountPaymentStatus closedAccountPaymentStatus;
  private final AccountRefundReason refundReason;
  private final Set<String> supportedFeeFineTypes;

  private FeeRefundProcessor(AccountPaymentStatus closedAccountPaymentStatus,
    AccountRefundReason refundReason, Collection<String> supportedFeeFineTypes) {

    this.closedAccountPaymentStatus = closedAccountPaymentStatus;
    this.refundReason = refundReason;
    this.supportedFeeFineTypes = new HashSet<>(supportedFeeFineTypes);
  }

  @Override
  public boolean canHandleAccountRefund(Account account) {
    return supportedFeeFineTypes.contains(account.getFeeFineType());
  }

  @Override
  public void onHasTransferAmount(AccountRefundContext context) {
    final Account account = context.getAccount();
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
      .withAction(closedAccountPaymentStatus)
      .withBalance(zeroFeeAmount())
      .withAmount(context.getAccount().getRemaining())
      .build());
  }

  @Override
  public void onRemainingAmountActionSaved(AccountRefundContext context) {
    context.closeAccount(closedAccountPaymentStatus);
  }

  private StoredFeeFineAction buildCreditAction(AccountRefundReason refundReason,
    FeeAmount actionAmount, String transactionInfo, AccountRefundContext context) {

    return populateCommonAttributes(context)
      .withAction(CREDITED_FULLY)
      .withAmount(actionAmount)
      .withPaymentMethod(refundReason.getValue())
      .withTransactionInformation(transactionInfo)
      .build();
  }

  private StoredFeeFineAction buildRefundAction(AccountRefundReason refundReason,
    FeeAmount actionAmount, String transactionInfo, AccountRefundContext context) {

    return populateCommonAttributes(context)
      .withAction(REFUNDED_FULLY)
      .withAmount(actionAmount)
      .withPaymentMethod(refundReason.getValue())
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

  private String getTransferRefundTransactionInfo(Account account) {
    return "Refund to " + account.getTransferAccountName();
  }

  public static FeeRefundProcessor createLostItemFeeRefundProcessor() {
    return new FeeRefundProcessor(CANCELLED_ITEM_RETURNED,
      LOST_ITEM_FOUND, asList(LOST_ITEM_FEE_TYPE, LOST_ITEM_PROCESSING_FEE_TYPE));
  }
}
