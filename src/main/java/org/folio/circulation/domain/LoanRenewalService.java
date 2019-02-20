package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.concurrent.CompletableFuture;

public class LoanRenewalService {
  private final LoanPolicyRepository loanPolicyRepository;

  public static LoanRenewalService using(Clients clients) {
    return new LoanRenewalService(new LoanPolicyRepository(clients));
  }

  private LoanRenewalService(LoanPolicyRepository loanPolicyRepository) {
    this.loanPolicyRepository = loanPolicyRepository;
  }

  public HttpResult<LoanAndRelatedRecords> renew(LoanAndRelatedRecords relatedRecords) {
    LoanPolicy loanPolicy = relatedRecords.getLoanPolicy();
    Loan loan = relatedRecords.getLoan();
    return loanPolicy.renew(loan, DateTime.now(DateTimeZone.UTC)).map(relatedRecords::withLoan);
  }

  public CompletableFuture<HttpResult<Loan>> overrideRenewal(Loan loan, DateTime dueDate, String comment) {
    return loanPolicyRepository.lookupLoanPolicy(loan)
      .thenApply(r -> r.next(policy -> policy.overrideRenewal(loan, DateTime.now(DateTimeZone.UTC), dueDate, comment)));
  }
}
