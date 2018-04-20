package org.folio.circulation.loanrules;

/**
 * Store the branch location.
 */
public class BranchLocation {
  /** UUID of the branch location. */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String id;
  /**
   * Set the branch.
   * @param UUID of the branch location.
   */
  public BranchLocation(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }
}
