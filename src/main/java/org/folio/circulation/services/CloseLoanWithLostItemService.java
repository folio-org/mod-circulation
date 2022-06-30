package org.folio.circulation.services;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.ActualCostRecordRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.DateFormatUtil;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_ACTUAL_COST_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.lostItemFeeTypes;
import static org.folio.circulation.support.results.Result.*;
import static org.folio.circulation.support.utils.ClockUtil.getZoneId;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
public class CloseLoanWithLostItemService {
  private final LoanRepository loanRepository;

  private final LostItemPolicyRepository lostItemPolicyRepository;

  private final ItemRepository itemRepository;

  private final AccountRepository accountRepository;

  private final EventPublisher eventPublisher;

  private final ActualCostRecordRepository actualCostRecordRepository;

  public CloseLoanWithLostItemService(LoanRepository loanRepository, ItemRepository itemRepository,
    AccountRepository accountRepository, LostItemPolicyRepository lostItemPolicyRepository,
    EventPublisher eventPublisher, ActualCostRecordRepository actualCostRecordRepository) {
    this.loanRepository = loanRepository;
    this.itemRepository = itemRepository;
    this.accountRepository = accountRepository;
    this.lostItemPolicyRepository = lostItemPolicyRepository;
    this.eventPublisher = eventPublisher;
    this.actualCostRecordRepository = actualCostRecordRepository;
  }

  public CompletableFuture<Result<Void>> tryCloseLoan(Loan loan) {
    if (loan == null || !loan.isItemLost()) {
      return completedFuture(Result.succeeded(null));
    }

    return closeLoanWithLostItemIfLostFeesResolved(loan);
  }

  private CompletableFuture<Result<Void>> closeLoanWithLostItemIfLostFeesResolved(Loan loan) {
    return accountRepository.findAccountsForLoan(loan)
      .thenComposeAsync(lostItemPolicyRepository::findLostItemPolicyForLoan)
      .thenComposeAsync(actualCostRecordRepository::findByLoan)
      .thenCompose(r -> r.after(l -> closeLoanAndUpdateItem(l, loanRepository,
        itemRepository, eventPublisher)))
      .thenCompose(r -> r.after(eventPublisher::publishClosedLoanEvent));
  }

  public CompletableFuture<Result<Loan>> closeLoanAndUpdateItem(Loan loan,
    LoanRepository loanRepository, ItemRepository itemRepository, EventPublisher eventPublisher) {

    if (!shouldCloseLoan(loan)) {
      return completedFuture(succeeded(loan));
    }

    boolean wasLoanOpen = loan.isOpen();
    loan.closeLoanAsLostAndPaid();

    return new StoreLoanAndItem(loanRepository, itemRepository).updateLoanAndItemInStorage(loan)
      .thenCompose(r -> r.after(l -> publishLoanClosedEvent(l, wasLoanOpen, eventPublisher)));
  }

  private CompletableFuture<Result<Loan>> publishLoanClosedEvent(Loan loan, boolean wasLoanOpen,
    EventPublisher eventPublisher) {

    return wasLoanOpen && loan.isClosed()
      ? eventPublisher.publishLoanClosedEvent(loan)
      : completedFuture(succeeded(loan));
  }

  private boolean allLostFeesClosed(Loan loan) {
    return loan.getAccounts().stream()
      .filter(account -> lostItemFeeTypes().contains(account.getFeeFineType()))
      .allMatch(Account::isClosed);
  }

  private boolean shouldCloseLoan(Loan loan) {
    if (allLostFeesClosed(loan)) {
      ActualCostRecord actualCostRecord = loan.getActualCostRecord();
      if(actualCostRecord == null) {
        return true;
      }
      if (loan.getAccounts().stream().noneMatch(account ->
        LOST_ITEM_ACTUAL_COST_FEE_TYPE.equals(account.getFeeFineType()))) {

        String expirationDate = actualCostRecord.getExpirationDate();

        return expirationDate != null && getZonedDateTime().isAfter(DateFormatUtil.parseDateTime(
          expirationDate, getZoneId()));
      } else {
        return true;
      }
    }

    return false;
  }
}
