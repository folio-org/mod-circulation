package org.folio.circulation.domain;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.folio.circulation.support.ClockManager.getClockManager;

import java.util.function.Predicate;

import org.apache.commons.lang3.ObjectUtils;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.resources.context.RenewalContext;
import org.joda.time.DateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

@With
@Getter
@AllArgsConstructor
public final class LoanToChargeOverdueFine {
  private final Loan loan;
  private final ItemStatus initialItemStatus;
  private final Predicate<OverdueFinePolicy> shouldChargeFinePredicate;
  private final boolean lostItemFeesRefundedOrCancelled;
  private final String loggedInUserId;

  private final Item item;
  private final FeeFineOwner feeFineOwner;
  private final FeeFine feeFine;
  private final User loggedInUser;

  public static LoanToChargeOverdueFine forCheckIn(CheckInContext checkInContext) {
    return new LoanToChargeOverdueFine(checkInContext.getLoan(),
      checkInContext.getItemStatusBeforeCheckIn(), policy -> !policy.isUnknown(),
      checkInContext.areLostItemFeesRefundedOrCancelled(), checkInContext.getLoggedInUserId(),
      null, null, null, null);
  }

  public static LoanToChargeOverdueFine forRenewal(RenewalContext renewalContext) {
    return new LoanToChargeOverdueFine(renewalContext.getLoanBeforeRenewal(),
      renewalContext.getItemStatusBeforeRenewal(),
      policy -> !policy.isUnknown() && isFalse(policy.getForgiveFineForRenewals()),
      renewalContext.isLostItemFeesRefundedOrCancelled(), renewalContext.getLoggedInUserId(),
      null, null, null, null);
  }

  public DateTime getReturnDate() {
    return loan.getReturnDate() != null
      ? loan.getReturnDate() : getClockManager().getDateTime();
  }

  public DateTime getDueDate() {
    if (initialItemStatus == ItemStatus.DECLARED_LOST) {
      return loan.getDeclareLostDateTime();
    }

    return loan.getDueDate();
  }

  public boolean isOverdue() {
    return getDueDate().isBefore(getReturnDate());
  }

  public boolean wasDeclaredLost() {
    return initialItemStatus == ItemStatus.DECLARED_LOST;
  }

  public String getLostItemPolicyId() {
    return loan.getLostItemPolicyId();
  }

  public String getOverdueFinePolicyId() {
    return loan.getOverdueFinePolicyId();
  }

  public OverdueFinePolicy getOverdueFinePolicy() {
    return loan.getOverdueFinePolicy();
  }

  public LoanToChargeOverdueFine withLoanPolicy(LoanPolicy loanPolicy) {
    return withLoan(loan.withLoanPolicy(loanPolicy));
  }

  public boolean shouldCountClosedPeriods() {
    return loan.getOverdueFinePolicy().getCountPeriodsWhenServicePointIsClosed();
  }

  public String getItemPrimaryServicePoint() {
    if (loan.getItem().getLocation() == null
      || loan.getItem().getLocation().getPrimaryServicePointId() == null) {
      return null;
    }

    return loan.getItem().getLocation().getPrimaryServicePointId().toString();
  }

  public boolean hasItemLocationPrimaryServicePoint() {
    return getItemPrimaryServicePoint() != null;
  }

  public boolean wasDueDateChangedByRecall() {
    return loan.wasDueDateChangedByRecall();
  }

  public boolean canCreateOverdueFine() {
    return ObjectUtils.allNotNull(loan, item, feeFineOwner, feeFine);
  }

  public boolean loanIsUndefined() {
    return loan == null;
  }

  public boolean shouldChargeOverdueFine() {
    if (!isOverdue()) {
      return false;
    }

    if (!shouldChargeFinePredicate.test(getLoan().getOverdueFinePolicy())) {
      return false;
    }

    if (wasDeclaredLost()) {
      return getLoan().getLostItemPolicy().shouldChargeOverdueFee()
        && isLostItemFeesRefundedOrCancelled();
    }

    return true;
  }
}
