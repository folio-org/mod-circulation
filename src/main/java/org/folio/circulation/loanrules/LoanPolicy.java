package org.folio.circulation.loanrules;

public class LoanPolicy {
  public String id;
  public LoanPolicy(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof LoanPolicy)) {
      return false;
    }
    LoanPolicy other = (LoanPolicy) o;
    if (id == null) {
      return other.id == null;
    }
    return id.equals(other.id);
  }

  @Override
  public int hashCode() {
    if (id == null) {
      return 0;
    }
    return id.hashCode();
  }
}
