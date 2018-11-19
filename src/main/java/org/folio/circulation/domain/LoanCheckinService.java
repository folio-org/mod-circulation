package org.folio.circulation.domain;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;

public class LoanCheckinService {
  private final LoanPolicyRepository loanPolicyRepository;

  public static LoanCheckinService using(Clients clients) {
    return new LoanCheckinService(new LoanPolicyRepository(clients));
  }

  private LoanCheckinService(LoanPolicyRepository loanPolicyRepository) {
    this.loanPolicyRepository = loanPolicyRepository;
  }

  public CompletableFuture<HttpResult<Loan>> checkin(Loan loan) {
    return loanPolicyRepository.lookupLoanPolicy(loan)
        .thenApply(r -> r.next(policy -> policy.checkin(loan, DateTime.now())));
  }
}
