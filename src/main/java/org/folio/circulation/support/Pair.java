package org.folio.circulation.support;

import org.folio.circulation.domain.Item;

public class Pair {
  private String key;
  private Item value;

  public Pair(String key, Item value) {
    this.key = key;
    this.value = value;
  }

  public String getKey() {
    return key;
  }

  public Item getValue() {
    return value;
  }
}
