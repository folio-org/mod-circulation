package org.folio.circulation.rules;

/**
 * Store the UUID of the patron group.
 */
public class PatronGroup {
  /** UUID of the patron group */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String id;

  /**
   * Set patron group.
   * @param id  UUID of the patron group
   */
  public PatronGroup(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }
}
