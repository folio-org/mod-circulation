package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ActualCostRecord.Status.CANCELLED;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_ACTUAL_COST_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.lostItemFeeTypes;
import static org.folio.circulation.support.results.Result.emptyAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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
      log.info("closeLoanAsLostAndPaid:: loan is null, closed, or item is not lost - skipping");
      return emptyAsync();
    }
    log.debug("closeLoanAsLostAndPaid:: parameters loanId: {}", loan::getId);

    return fetchLoanFeeFineData(loan)
      .thenCompose(r -> r.after(this::closeLoanWithLostItemFeesPaidAndPublishEvents));
  }

  private CompletableFuture<Result<Void>> closeLoanWithLostItemFeesPaidAndPublishEvents(
    Loan loan) {

    log.debug("closeLoanWithLostItemFeesPaidAndPublishEvents:: loanId: {}", loan::getId);
    return closeLoanAsLostAndPaid(loan, loanRepository, itemRepository, eventPublisher)
      .thenCompose(r -> r.after(eventPublisher::publishClosedLoanEvent));
  }

  private CompletableFuture<Result<Loan>> fetchLoanFeeFineData(Loan loan) {
    log.debug("fetchLoanFeeFineData:: parameters loanId: {}", loan::getId);
    return accountRepository.findAccountsForLoan(loan)
      .thenComposeAsync(lostItemPolicyRepository::findLostItemPolicyForLoan)
      .thenComposeAsync(r -> r.after(actualCostRecordRepository::findByLoan));
  }

  private CompletableFuture<Result<Loan>> closeLoanAsLostAndPaid(Loan loan,
    LoanRepository loanRepository, ItemRepository itemRepository, EventPublisher eventPublisher) {

    log.debug("closeLoanAsLostAndPaid:: loanId: {}", loan::getId);
    if (!shouldCloseLoan(loan)) {
      log.info("closeLoanAsLostAndPaid:: loan {} should not be closed", loan::getId);
      return completedFuture(succeeded(loan));
    }

    boolean wasLoanOpen = loan.isOpen();
    loan.closeLoanAsLostAndPaid();
    log.info("closeLoanAsLostAndPaid:: closing loan {} as lost and paid", loan::getId);

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
        log.info("shouldCloseLoan:: all lost fees closed and no open actual cost record for loanId: {}",
          loan::getId);
        return true;
      }
      if (loan.getAccounts().stream().noneMatch(account ->
        LOST_ITEM_ACTUAL_COST_FEE_TYPE.equals(account.getFeeFineType()))) {

        ZonedDateTime expirationDate = actualCostRecord.getExpirationDate();

        return expirationDate != null && getZonedDateTime().isAfter(expirationDate);
      } else {
        log.info("shouldCloseLoan:: actual cost fee account exists for loanId: {}", loan::getId);
        return true;
      }
    }

    log.info("shouldCloseLoan:: not all lost fees are closed for loanId: {}", loan::getId);
    return false;
  }
}
