package org.folio.circulation.services;

import static org.folio.circulation.infrastructure.storage.inventory.ItemRepository.noLocationMaterialTypeAndLoanTypeInstance;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.domain.representations.ChangeItemStatusRequest;
import org.folio.circulation.domain.validation.LoanValidator;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;

public class ChangeItemStatusService {
  private final LoanRepository loanRepository;
  private final StoreLoanAndItem storeLoanAndItem;

  public ChangeItemStatusService(Clients clients) {
    this.loanRepository = new LoanRepository(clients);
    this.storeLoanAndItem = new StoreLoanAndItem(loanRepository,
      noLocationMaterialTypeAndLoanTypeInstance(clients));
  }

  public <T extends ChangeItemStatusRequest> CompletableFuture<Result<Loan>> getOpenLoan(T request) {
    return loanRepository.getById(request.getLoanId())
      .thenApply(LoanValidator::refuseWhenLoanIsClosed);
  }

  public CompletableFuture<Result<Loan>> updateLoanAndItem(Result<Loan> loanResult) {
    return loanResult.after(storeLoanAndItem::updateLoanAndItemInStorage);
  }
}
