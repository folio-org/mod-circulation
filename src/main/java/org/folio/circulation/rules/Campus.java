package org.folio.circulation.rules;

/**
 * Store the branch location.
 */
public class Campus {
  /** UUID of the campus. */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String id;
  /**
   * Set the branch.
   * @param UUID of the campus.
   */
  public Campus(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }
}
