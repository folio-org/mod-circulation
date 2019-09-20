package org.folio.circulation.rules;

/**
 * Store UUID of the campus location.
 */
public class Institution {
  /** UUID of the institution location. */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String id;
  /**
   * Set campus.
   * @param id  UUID of the institution.
   */
  public Institution(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }
}
