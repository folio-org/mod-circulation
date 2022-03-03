package org.folio.circulation.support.utils;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CollectionUtil {
  private CollectionUtil() {}

  public static <T, R> Set<R> nonNullUniqueSetOf(Collection<T> collection, Function<T, R> mapper) {
    return collection.stream()
      .filter(Objects::nonNull)
      .map(mapper)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }
}
