package org.folio.circulation.rules;

import org.folio.circulation.domain.Item;

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

  @EqualsAndHashCode.Exclude
  private Item item;

  public CirculationRuleCriteria(@NonNull Item item, @NonNull String patronGroupId) {
    this.materialTypeId = item.getMaterialTypeId();
    this.loanTypeId = item.getLoanTypeId();
    this.locationId = item.getEffectiveLocationId();
    this.patronGroupId = patronGroupId;
    this.item = item;
  }
}
