package org.folio.circulation.loanrules;

/**
 * Store the UUID of the shelving location.
 */
public class ShelvingLocation {
  /** UUID of the shelving location. */
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
