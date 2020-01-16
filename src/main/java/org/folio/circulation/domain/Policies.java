package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LostItemPolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicy;

public class Policies {
  private final LoanPolicy loanPolicy;
  private final OverdueFinePolicy overdueFinePolicy;
  private final LostItemPolicy lostItemPolicy;

  Policies(LoanPolicy loanPolicy, OverdueFinePolicy overdueFinePolicy,
    LostItemPolicy lostItemPolicy) {
    this.loanPolicy = loanPolicy;
    this.overdueFinePolicy = overdueFinePolicy;
    this.lostItemPolicy = lostItemPolicy;
  }

  public LoanPolicy getLoanPolicy() {
    return loanPolicy;
  }

  public OverdueFinePolicy getOverdueFinePolicy() {
    return overdueFinePolicy;
  }

  public LostItemPolicy getLostItemPolicy() {
    return lostItemPolicy;
  }

  public Policies withLoanPolicy(LoanPolicy newLoanPolicy) {
    return new Policies(newLoanPolicy, overdueFinePolicy, lostItemPolicy);
  }

  public Policies withOverdueFinePolicy(OverdueFinePolicy newOverdueFinePolicy) {
    return new Policies(loanPolicy, newOverdueFinePolicy, lostItemPolicy);
  }

  public Policies withLostItemPolicy(LostItemPolicy newLostItemPolicy) {
    return new Policies(loanPolicy, overdueFinePolicy, newLostItemPolicy);
  }
}
