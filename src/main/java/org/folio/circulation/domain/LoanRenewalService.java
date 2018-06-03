package org.folio.circulation.domain;

import org.folio.circulation.support.HttpResult;

public class LoanRenewalService {
  public HttpResult<Loan> renew(Loan loan) {
    loan.renew();

    return HttpResult.success(loan);
  }
}
