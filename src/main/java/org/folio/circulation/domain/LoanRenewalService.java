package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;

import java.util.concurrent.CompletableFuture;

public class LoanRenewalService {
  private final LoanPolicyRepository loanPolicyRepository;

  public static LoanRenewalService using(Clients clients) {
    return new LoanRenewalService(new LoanPolicyRepository(clients));
  }

  private LoanRenewalService(LoanPolicyRepository loanPolicyRepository) {
    this.loanPolicyRepository = loanPolicyRepository;
  }

  public CompletableFuture<HttpResult<Loan>> renew(Loan loan) {
    return loanPolicyRepository.lookupLoanPolicy(loan)
      .thenApply(r -> r.next(loanPolicy -> loanPolicy.renew(loan)));
  }
}
