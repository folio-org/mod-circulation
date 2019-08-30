package org.folio.circulation.rules;

/**
 * Store the UUID of the shelving location.
 */
public class Location {
  /** UUID of the  location. */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String id;

  /** Set location.
   * @param id  UUID of the location.
   */
  public Location(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }
}
