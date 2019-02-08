package org.folio.circulation.circulationrules;

/**
 * Store UUID of the campus location.
 */
public class CampusLocation {
  /** UUID of the campus location. */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String id;
  /**
   * Set campus.
   * @param id  UUID of the campus location.
   */
  public CampusLocation(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }
}
