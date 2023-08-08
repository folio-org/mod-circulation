package org.folio.circulation.rules;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.User;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public class CirculationRuleCriteria {
  private final String materialTypeId;
  private final String loanTypeId;
  private final String locationId;
  private final String patronGroupId;

  public CirculationRuleCriteria(@NonNull Item item, @NonNull User user) {
    this.materialTypeId = item.getMaterialTypeId();
    this.loanTypeId = item.getLoanTypeId();
    this.locationId = item.getEffectiveLocationId();
    this.patronGroupId = user.getPatronGroupId();
  }
}
