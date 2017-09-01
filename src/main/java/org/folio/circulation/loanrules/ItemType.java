package org.folio.circulation.loanrules;

public class ItemType {
  public String id;
  public ItemType(String id) {
    this.id = id;
  }
  @Override
  public String toString() {
    return id;
  }
}
