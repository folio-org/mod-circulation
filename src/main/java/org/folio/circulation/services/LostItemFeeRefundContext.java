package org.folio.circulation.services;

import static java.util.Collections.emptyList;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.domain.ItemStatus.AGED_TO_LOST;
import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.domain.ItemStatus.LOST_AND_PAID;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.services.support.RefundAccountCommand;
import org.joda.time.DateTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

@With(AccessLevel.PACKAGE)
@Getter(AccessLevel.PACKAGE)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
final class LostItemFeeRefundContext {
  private final ItemStatus itemStatus;
  private final String itemId;
  private final String staffUserId;
  private final String servicePointId;
  private final Loan loan;
  private final Collection<Account> accounts;
  private final LostItemPolicy lostItemPolicy;

  private Collection<Account> accountsNeedingRefunds() {
    if (!lostItemPolicy.isRefundProcessingFeeWhenReturned()) {
      return accounts.stream()
        .filter(account -> !account.getFeeFineType().equals(LOST_ITEM_PROCESSING_FEE_TYPE))
        .collect(Collectors.toList());
    }

    return accounts;
  }

  List<RefundAccountCommand> accountRefundCommands() {
    return accountsNeedingRefunds().stream()
      .map(account -> new RefundAccountCommand(account, staffUserId, servicePointId))
      .collect(Collectors.toList());
  }

  boolean anyAccountNeedsRefund() {
    return !accountsNeedingRefunds().isEmpty();
  }

  DateTime getItemLostDate() {
    if(itemStatus == DECLARED_LOST) {
      return loan.getDeclareLostDateTime();
    }

    if(itemStatus == LOST_AND_PAID) {
      return loan.getDeclareLostDateTime();
    }

    if (itemStatus == AGED_TO_LOST) {
      return loan.getAgedToLostDateTime();
    }

    return null;
  }

  boolean itemIsNotLost() {
    return itemStatus != DECLARED_LOST && itemStatus != AGED_TO_LOST
      && itemStatus != LOST_AND_PAID;
  }

  boolean hasLoan() {
    return loan != null;
  }

  public static LostItemFeeRefundContext using(CheckInContext context) {
    return new LostItemFeeRefundContext(context.getItemStatusBeforeCheckIn(),
      context.getItem().getItemId(), context.getLoggedInUserId(),
      context.getCheckInServicePointId().toString(), context.getLoan(),
      emptyList(), null);
  }

  public static LostItemFeeRefundContext using(RenewalContext context, String servicePointId) {
    return new LostItemFeeRefundContext(context.getItemStatusBeforeRenewal(),
      context.getLoan().getItemId(), context.getLoggedInUserId(),
      servicePointId, context.getLoan(), emptyList(), null);
  }
}
