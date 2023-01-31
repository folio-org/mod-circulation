package org.folio.circulation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ActualCostFeeCancelReason {
  LOST_ITEM_RETURNED("Lost item was returned"),
  LOST_ITEM_RENEWED("Lost item was renewed"),
  AGED_TO_LOST_ITEM_DECLARED_LOST("Aged to lost item was declared lost");

  private final String value;
}
