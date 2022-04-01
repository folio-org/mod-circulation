package org.folio.circulation.domain;

import lombok.Value;

@Value
public class ItemStatus {
  ItemStatusName name;
  String date;

  public String getValue() {
    return name.getName();
  }

  public boolean is(ItemStatusName name) {
    return this.name.equals(name);
  }

  public boolean isLostNotResolved() {
    return getName().isLostNotResolved();
  }
}
