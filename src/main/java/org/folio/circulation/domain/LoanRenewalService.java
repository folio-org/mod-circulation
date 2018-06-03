package org.folio.circulation.domain;

import org.folio.circulation.support.HttpResult;

import java.util.concurrent.CompletableFuture;

public class LoanRenewalService {
  public CompletableFuture<HttpResult<Loan>> renew(Loan loan) {
    loan.renew();

    return CompletableFuture.completedFuture(HttpResult.success(loan));
  }
}
