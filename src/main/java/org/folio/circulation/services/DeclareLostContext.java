package org.folio.circulation.services;

import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.domain.representations.DeclareItemLostRequest;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;

@With
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DeclareLostContext {
  private Loan loan;
  private DeclareItemLostRequest request;
  private FeeFineOwner feeFineOwner;

  public DeclareLostContext(Loan loan, DeclareItemLostRequest request) {
    this.loan = loan;
    this.request = request;
  }

  public DeclareLostContext withLostItemPolicy(LostItemPolicy lostItemPolicy) {
    return withLoan(loan.withLostItemPolicy(lostItemPolicy));
  }
}

