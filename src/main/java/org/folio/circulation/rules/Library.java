package org.folio.circulation.rules;

/**
 * Store the collection location.
 */
public class Library {
  /** UUID of the library. */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String id;
  /**
   * Set collection.
   * @param id  UUID of the library.
   */
  public Library(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }
}
