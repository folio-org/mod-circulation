package org.folio.circulation.domain;

import lombok.Value;

@Value
public class ItemStatus {
  public static ItemStatus unknown() {
    return new ItemStatus(ItemStatusName.NONE, null);
  }

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
