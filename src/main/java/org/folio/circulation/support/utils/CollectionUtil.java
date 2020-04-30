package org.folio.circulation.support.utils;

import java.util.Collection;

public final class CollectionUtil {

  private CollectionUtil() {}

  public static <T> T firstOrNull(Collection<T> collection) {
    return collection.stream()
      .findFirst()
      .orElse(null);
  }
}
