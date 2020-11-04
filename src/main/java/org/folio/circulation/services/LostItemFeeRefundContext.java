package org.folio.circulation.services;

import static org.folio.circulation.domain.AccountCancelReason.CANCELLED_ITEM_RENEWED;
import static org.folio.circulation.domain.AccountCancelReason.CANCELLED_ITEM_RETURNED;
import static org.folio.circulation.domain.AccountRefundReason.LOST_ITEM_FOUND;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.domain.ItemStatus.LOST_AND_PAID;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.AccountCancelReason;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.services.support.AccountRefundCancelCommand;
import org.joda.time.DateTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

@With(AccessLevel.PACKAGE)
@Getter(AccessLevel.PACKAGE)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
final class LostItemFeeRefundContext {
  private final ItemStatus initialItemStatus;
  private final String itemId;
  private final String staffUserId;
  private final String servicePointId;
  private final Loan loan;
  private final AccountCancelReason cancelReason;

  private Collection<Account> accountsNeedingRefunds() {
    if (!getLostItemPolicy().isRefundProcessingFeeWhenReturned()) {
      return loan.getAccounts().stream()
        .filter(account -> !account.getFeeFineType().equals(LOST_ITEM_PROCESSING_FEE_TYPE))
        .collect(Collectors.toList());
    }

    return loan.getAccounts();
  }

  LostItemFeeRefundContext withAccounts(Collection<Account> accounts) {
    return withLoan(loan.withAccounts(accounts));
  }

  LostItemFeeRefundContext withLostItemPolicy(LostItemPolicy lostItemPolicy) {
    return withLoan(loan.withLostItemPolicy(lostItemPolicy));
  }

  List<AccountRefundCancelCommand> accountRefundCommands() {
    return accountsNeedingRefunds().stream()
      .map(this::createCommand)
      .collect(Collectors.toList());
  }

  private AccountRefundCancelCommand createCommand(Account account) {
    return new AccountRefundCancelCommand(account, staffUserId, servicePointId,
      LOST_ITEM_FOUND, cancelReason);
  }

  DateTime getItemLostDate() {
    return loan.getLostDate();
  }

  boolean shouldRefundFeesForItem() {
    return initialItemStatus.isLostNotResolved() || initialItemStatus == LOST_AND_PAID;
  }

  boolean hasLoan() {
    return loan != null;
  }

  LostItemPolicy getLostItemPolicy() {
    return loan.getLostItemPolicy();
  }

  static LostItemFeeRefundContext forCheckIn(CheckInContext context) {
    return new LostItemFeeRefundContext(context.getItemStatusBeforeCheckIn(),
      context.getItem().getItemId(), context.getLoggedInUserId(),
      context.getCheckInServicePointId().toString(), context.getLoan(), CANCELLED_ITEM_RETURNED);
  }

  static LostItemFeeRefundContext forRenewal(RenewalContext context, String servicePointId) {
    return new LostItemFeeRefundContext(context.getItemStatusBeforeRenewal(),
      context.getLoan().getItemId(), context.getLoggedInUserId(),
      servicePointId, context.getLoan(), CANCELLED_ITEM_RENEWED);
  }
}
