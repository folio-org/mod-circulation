package org.folio.circulation.domain;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
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

  public Result<LoanAndRelatedRecords> renew(LoanAndRelatedRecords relatedRecords) {
    Loan loan = relatedRecords.getLoan();
    LoanPolicy loanPolicy = loan.getLoanPolicy();

    return loanPolicy.renew(loan, DateTime.now(DateTimeZone.UTC))
      .map(relatedRecords::withLoan);
  }

  public CompletableFuture<Result<Loan>> overrideRenewal(Loan loan,
    DateTime dueDate, String comment) {

    return loanPolicyRepository.lookupPolicy(loan)
      .thenApply(r -> r.next(policy -> policy.overrideRenewal(loan,
        DateTime.now(DateTimeZone.UTC), dueDate, comment)));
  }
}
