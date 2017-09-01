package org.folio.circulation.loanrules;

public class ShelvingLocation {
  public String id;
  public ShelvingLocation(String id) {
    this.id = id;
  }
  @Override
  public String toString() {
    return id;
  }
}
