package org.folio.circulation.rules;

/**
 * Store the UUID of the item's type (the material type).
 */
public class ItemType {
  /** UUID of the item type. */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String id;

  /**
   * Set item type.
   * @param id  UUID of the item type
   */
  public ItemType(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }
}
