package org.folio.circulation.domain.anonymization;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.support.Result;

public class LoansForTenantFinder extends DefaultLoansFinder {

  private final LoanRepository loanRepository;

  public LoansForTenantFinder(LoanAnonymizationFacade anonymization) {
    super(anonymization);
    loanRepository = new LoanRepository(anonymization.clients());
  }

  @Override
  public CompletableFuture<Result<Collection<Loan>>> findLoansToAnonymize() {
    return loanRepository.findClosedLoans(anonymization.getFetchLoansLimit())
      .thenCompose(this::fillLoanInformation);
  }
}
