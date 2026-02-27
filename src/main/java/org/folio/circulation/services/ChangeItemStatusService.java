package org.folio.circulation.services;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.representations.ChangeItemStatusRequest;
import org.folio.circulation.domain.validation.LoanValidator;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.results.Result;

public class ChangeItemStatusService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final LoanRepository loanRepository;
  private final StoreLoanAndItem storeLoanAndItem;

  public ChangeItemStatusService(LoanRepository loanRepository,
    StoreLoanAndItem storeLoanAndItem) {

    this.loanRepository = loanRepository;
    this.storeLoanAndItem = storeLoanAndItem;
  }

  public <T extends ChangeItemStatusRequest> CompletableFuture<Result<Loan>> getOpenLoan(T request) {
    log.info("getOpenLoan:: parameters request loanId: {}", request::getLoanId);
    return loanRepository.getById(request.getLoanId())
      .thenApply(LoanValidator::refuseWhenLoanIsClosed);
  }

  public CompletableFuture<Result<Loan>> updateLoanAndItem(Result<Loan> loanResult) {
    log.info("updateLoanAndItem:: updating loan and item in storage");
    return loanResult.after(storeLoanAndItem::updateLoanAndItemInStorage);
  }
}
