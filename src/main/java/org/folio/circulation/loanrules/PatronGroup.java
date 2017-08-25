package org.folio.circulation.loanrules;

public class PatronGroup {
  public String name;
  public PatronGroup(String name) {
    this.name = name;
  }
  @Override
  public String toString() {
    return name;
  }
}
