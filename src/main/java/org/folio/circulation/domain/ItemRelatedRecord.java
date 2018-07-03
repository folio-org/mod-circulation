package org.folio.circulation.domain;

public interface ItemRelatedRecord<T> {
  String getItemId();
  T withItem(Item item);
}
