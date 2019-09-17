package org.folio.circulation.domain.anonymization;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.support.Result;

public class LoansForBorrowerFinder extends DefaultLoansFinder {

  private final LoanRepository loanRepository;
  private String userId;

  public LoansForBorrowerFinder(LoanAnonymizationFacade anonymization, String userId) {
    super(anonymization);
    this.userId = userId;
    loanRepository = new LoanRepository(anonymization.clients());
  }

  @Override
  public CompletableFuture<Result<Collection<Loan>>> findLoansToAnonymize() {

    return loanRepository.findClosedLoansForUser(userId, anonymization.getFetchLoansLimit())
      .thenCompose(this::fillLoanInformation);
  }
}
