package org.folio.circulation.loanrules;

/**
 * Store the UUID of the item's loan type.
 */
public class LoanType {
  /** UUID of the item's loan type */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String id;

  /**
   * Set loan type.
   * @param id  UUID of the item's loan type.
   */
  public LoanType(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }
}
