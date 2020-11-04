package org.folio.circulation.domain;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

@AllArgsConstructor
@Getter
@With
public class LostItemRequiringCostsFee {
  private final Item item;
  private final Loan loan;
  private final FeeFineOwner feeFineOwner;

  public LostItemRequiringCostsFee(Loan loan) {
    this.item = null;
    this.loan = loan;
    this.feeFineOwner = null;
  }

  public String getOwnerServicePointId() {
    if (item == null) {
      return null;
    }
    return item.getPermanentLocation().getPrimaryServicePointId().toString();
  }

  public LostItemRequiringCostsFee withFeeFineOwners(Map<String, FeeFineOwner> owners) {
    return withFeeFineOwner(owners.get(getOwnerServicePointId()));
  }

}
