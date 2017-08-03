package org.folio.circulation.loanrules;

public class ItemType {
  public String name;
  public ItemType(String name) {
    this.name = name;
  }
  @Override
  public String toString() {
    return name;
  }
}
