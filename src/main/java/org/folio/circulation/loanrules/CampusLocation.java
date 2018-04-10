package org.folio.circulation.loanrules;

/**
 * Store UUID of the campus location.
 */
public class CampusLocation {
  /** UUID of the campus location. */
  public String id;
  /**
   * Set UUID of the campus location.
   */
  public CampusLocation(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }
}
