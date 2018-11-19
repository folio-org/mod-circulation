package org.folio.circulation.domain;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;

public class LoanCheckinService {

  private final CollectionResourceClient itemsStorageClient;

  private final LoanPolicyRepository loanPolicyRepository;

  public static LoanCheckinService using(Clients clients) {
    return new LoanCheckinService(clients);
  }

  private LoanCheckinService(Clients clients) {
    this.itemsStorageClient = clients.itemsStorage();
    this.loanPolicyRepository = new LoanPolicyRepository(clients);
  }

  public CompletableFuture<HttpResult<Loan>> checkin(Loan loan) {
    return loanPolicyRepository.lookupLoanPolicy(loan)
        .thenApply(r -> r.next(policy -> policy.checkin(loan, DateTime.now())));
  }

}
