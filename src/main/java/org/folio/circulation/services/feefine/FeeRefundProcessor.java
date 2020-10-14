package org.folio.circulation.services.feefine;

import static org.folio.circulation.domain.AccountRefundReason.LOST_ITEM_FOUND;
import static org.folio.circulation.domain.FeeAmount.zeroFeeAmount;
import static org.folio.circulation.domain.FeeFine.lostItemFeeTypes;
import static org.folio.circulation.domain.representations.AccountPaymentStatus.CREDITED_FULLY;
import static org.folio.circulation.domain.representations.AccountPaymentStatus.REFUNDED_FULLY;
import static org.folio.circulation.domain.representations.StoredFeeFineAction.StoredFeeFineActionBuilder;
import static org.folio.circulation.support.ClockManager.getClockManager;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.AccountRefundReason;
import org.folio.circulation.domain.FeeAmount;
import org.folio.circulation.domain.representations.StoredFeeFineAction;
import org.joda.time.DateTime;

public class FeeRefundProcessor implements AccountRefundProcessor {
  private final AccountRefundReason refundReason;
  private final Set<String> supportedFeeFineTypes;

  private FeeRefundProcessor(AccountRefundReason refundReason, Collection<String> supportedFeeFineTypes) {
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

    context.subtractRemainingAmount(account.getTransferredAmount());

    context.addActions(buildCreditAction(refundReason, account.getTransferredAmount(),
      getTransferCreditTransactionInfo(account), context));

    context.addRemainingAmount(account.getTransferredAmount());

    context.addActions(buildRefundAction(refundReason, account.getTransferredAmount(),
      getTransferRefundTransactionInfo(account), context));
  }

  @Override
  public void onTransferAmountRefundActionSaved(AccountRefundContext context) {
    context.setPaymentStatus(REFUNDED_FULLY);
  }

  @Override
  public void onHasPaidAmount(AccountRefundContext context) {
    final Account account = context.getAccount();

    context.subtractRemainingAmount(account.getPaidAmount());

    context.addActions(buildCreditAction(refundReason, account.getPaidAmount(),
      "Refund to patron", context));

    context.addRemainingAmount(account.getPaidAmount());

    context.addActions(buildRefundAction(refundReason, account.getPaidAmount(),
      "Refunded to patron", context));
  }

  @Override
  public void onPaidAmountRefundActionSaved(AccountRefundContext context) {
    context.setPaymentStatus(REFUNDED_FULLY);
  }

  @Override
  public void onHasRemainingAmount(AccountRefundContext context) {
    context.addActions(populateCommonAttributes(context)
      .withAction(context.getCancellationReason())
      .withBalance(zeroFeeAmount())
      .withAmount(context.getAccount().getRemaining())
      .build());
  }

  @Override
  public void onRemainingAmountActionSaved(AccountRefundContext context) {
    context.closeAccount(context.getCancellationReason());
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
      .withCreatedAt(context.getServicePoint())
      .withCreatedBy(context.getUser())
      .withBalance(context.getAccount().getRemaining())
      .withActionDate(getActionDate(context));
  }

  private DateTime getActionDate(AccountRefundContext context) {
    // Update action dateTime to keep theirs historical order
    // actionDate = now() + Nms
    // Because they are created very quickly
    // So the ISO date time is the same for all actions
    return getClockManager().getDateTime().plusMillis(context.getActions().size());
  }

  private String getTransferRefundTransactionInfo(Account account) {
    return "Refunded to " + account.getTransferAccountName();
  }

  private String getTransferCreditTransactionInfo(Account account) {
    return "Refund to " + account.getTransferAccountName();
  }

  public static FeeRefundProcessor createLostItemFeeRefundProcessor() {
    return new FeeRefundProcessor(LOST_ITEM_FOUND, lostItemFeeTypes());
  }
}
