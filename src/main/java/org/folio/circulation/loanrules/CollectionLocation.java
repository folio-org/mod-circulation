package org.folio.circulation.loanrules;

/**
 * Store the collection location.
 */
public class CollectionLocation {
  /** UUID of the collection location. */
  public String id;
  /**
   * Set UUID of the collection location.
   */
  public CollectionLocation(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }
}
