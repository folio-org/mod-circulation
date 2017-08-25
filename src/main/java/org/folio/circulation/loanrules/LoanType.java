package org.folio.circulation.loanrules;

public class LoanType {
  public String name;
  public LoanType(String name) {
    this.name = name;
  }
  @Override
  public String toString() {
    return name;
  }
}
