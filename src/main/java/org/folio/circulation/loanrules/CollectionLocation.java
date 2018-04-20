package org.folio.circulation.loanrules;

/**
 * Store the collection location.
 */
public class CollectionLocation {
  /** UUID of the collection location. */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String id;
  /**
   * Set collection.
   * @param id  UUID of the collection location.
   */
  public CollectionLocation(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }
}
