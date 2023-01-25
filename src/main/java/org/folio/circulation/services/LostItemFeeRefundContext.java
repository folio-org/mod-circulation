package org.folio.circulation.services;

import static org.folio.circulation.domain.AccountCancelReason.CANCELLED_ITEM_RENEWED;
import static org.folio.circulation.domain.AccountCancelReason.CANCELLED_ITEM_RETURNED;
import static org.folio.circulation.domain.AccountRefundReason.LOST_ITEM_FOUND;
import static org.folio.circulation.domain.ActualCostFeeCancelReason.LOST_ITEM_RENEWED;
import static org.folio.circulation.domain.ActualCostFeeCancelReason.LOST_ITEM_RETURNED;
import static org.folio.circulation.domain.ItemStatus.LOST_AND_PAID;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.AccountCancelReason;
import org.folio.circulation.domain.ActualCostFeeCancelReason;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.services.support.RefundAndCancelAccountCommand;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

@With(AccessLevel.PACKAGE)
@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class LostItemFeeRefundContext {
  private final ItemStatus initialItemStatus;
  private final String itemId;
  private final String staffUserId;
  private final String servicePointId;
  private final Loan loan;
  private final AccountCancelReason cancelReason;
  private final ActualCostFeeCancelReason actualCostFeeCancelReason;

  LostItemFeeRefundContext withAccounts(Collection<Account> accounts) {
    return withLoan(loan.withAccounts(accounts));
  }

  LostItemFeeRefundContext withLostItemPolicy(LostItemPolicy lostItemPolicy) {
    return withLoan(loan.withLostItemPolicy(lostItemPolicy));
  }

  List<RefundAndCancelAccountCommand> accountRefundCommands() {
    return loan.getAccounts().stream()
      .map(this::createCommand)
      .collect(Collectors.toList());
  }

  private RefundAndCancelAccountCommand createCommand(Account account) {
    return new RefundAndCancelAccountCommand(account, staffUserId, servicePointId,
      LOST_ITEM_FOUND, cancelReason);
  }

  ZonedDateTime getItemLostDate() {
    return loan.getLostDate();
  }

  String getLoanId() {
    return Optional.ofNullable(loan)
      .map(Loan::getId)
      .orElse(null);
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
      context.getCheckInServicePointId().toString(), context.getLoan(), CANCELLED_ITEM_RETURNED,
      LOST_ITEM_RETURNED);
  }

  static LostItemFeeRefundContext forRenewal(RenewalContext context, String servicePointId) {
    return new LostItemFeeRefundContext(context.getItemStatusBeforeRenewal(),
      context.getLoan().getItemId(), context.getLoggedInUserId(),
      servicePointId, context.getLoan(), CANCELLED_ITEM_RENEWED, LOST_ITEM_RENEWED);
  }
}
