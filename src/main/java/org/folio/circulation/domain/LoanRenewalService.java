package org.folio.circulation.domain;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

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
      .thenApply(r -> r.next(policy -> policy.renew(loan, DateTime.now(DateTimeZone.UTC))));
  }

  public CompletableFuture<HttpResult<Loan>> overrideRenewal(Loan loan, DateTime dueDate, String comment) {
    return loanPolicyRepository.lookupLoanPolicy(loan)
      .thenApply(r -> r.next(policy -> policy.overrideRenewal(loan, DateTime.now(DateTimeZone.UTC), dueDate, comment)));
  }
}
