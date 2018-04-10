package org.folio.circulation.loanrules;

/**
 * Store the branch location.
 */
public class BranchLocation {
  /** UUID of the branch location. */
  public String id;
  /**
   * Set UUID of the branch location.
   */
  public BranchLocation(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }
}
