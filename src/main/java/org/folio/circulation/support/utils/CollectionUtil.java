package org.folio.circulation.support.utils;

import java.util.Collection;

import org.folio.circulation.domain.MultipleRecords;

public final class CollectionUtil {

  private CollectionUtil() {}

  public static <T> T firstOrNull(Collection<T> collection) {
    if (collection == null) {
      return null;
    }

    return collection.stream()
      .findFirst()
      .orElse(null);
  }

  public static <T> T firstOrNull(MultipleRecords<T> records) {
    if (records == null) {
      return null;
    }

    return firstOrNull(records.getRecords());
  }
}
