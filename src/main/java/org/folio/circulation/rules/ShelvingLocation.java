package org.folio.circulation.rules;

/**
 * Store the UUID of the shelving location.
 */
public class ShelvingLocation {
  /** UUID of the shelving location. */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String id;

  /** Set shelving location.
   * @param id  UUID of the shelving location.
   */
  public ShelvingLocation(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }
}
