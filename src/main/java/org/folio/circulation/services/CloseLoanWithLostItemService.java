package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ActualCostRecord.Status.CANCELLED;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_ACTUAL_COST_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.lostItemFeeTypes;
import static org.folio.circulation.support.results.Result.emptyAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;

import java.time.ZonedDateTime;
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

  public CompletableFuture<Result<Void>> closeLoanAsLostAndPaid(Loan loan) {
    if (loan == null || loan.isClosed() || !loan.isItemLost()) {
      return emptyAsync();
    }

    return fetchLoanFeeFineData(loan)
      .thenCompose(r -> r.after(this::closeLoanWithLostItemFeesPaidAndPublishEvents));
  }

  private CompletableFuture<Result<Void>> closeLoanWithLostItemFeesPaidAndPublishEvents(
    Loan loan) {

    return closeLoanAsLostAndPaid(loan, loanRepository, itemRepository, eventPublisher)
      .thenCompose(r -> r.after(eventPublisher::publishClosedLoanEvent));
  }

  private CompletableFuture<Result<Loan>> fetchLoanFeeFineData(Loan loan) {
    return accountRepository.findAccountsForLoan(loan)
      .thenComposeAsync(lostItemPolicyRepository::findLostItemPolicyForLoan)
      .thenComposeAsync(r -> r.after(actualCostRecordRepository::findByLoan));
  }

  private CompletableFuture<Result<Loan>> closeLoanAsLostAndPaid(Loan loan,
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
      if (actualCostRecord == null || actualCostRecord.getStatus() == CANCELLED) {
        return true;
      }
      if (loan.getAccounts().stream().noneMatch(account ->
        LOST_ITEM_ACTUAL_COST_FEE_TYPE.equals(account.getFeeFineType()))) {

        ZonedDateTime expirationDate = actualCostRecord.getExpirationDate();

        return expirationDate != null && getZonedDateTime().isAfter(expirationDate);
      } else {
        return true;
      }
    }

    return false;
  }
}
