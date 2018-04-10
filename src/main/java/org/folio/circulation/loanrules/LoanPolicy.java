package org.folio.circulation.loanrules;

/**
 * Store the UUID of the loan policy.
 */
public class LoanPolicy {
  /** UUID of the loan policy. */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String id;

  /**
   * Set loan policy.
   * @param id  UUID of the loan policy.
   */
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
