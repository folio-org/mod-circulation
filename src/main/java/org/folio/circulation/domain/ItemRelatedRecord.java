package org.folio.circulation.domain;

public interface ItemRelatedRecord {
  String getItemId();
  ItemRelatedRecord withItem(Item item);

  Item getItem();
}
