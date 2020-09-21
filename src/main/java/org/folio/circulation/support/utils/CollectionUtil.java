package org.folio.circulation.support.utils;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

  public static <T, R> Set<R> uniqueSetOf(Collection<T> collection, Function<T, R> mapper) {
    return collection.stream()
      .map(mapper)
      .collect(Collectors.toSet());
  }

  public static <T, R> Set<R> nonNullUniqueSetOf(Collection<T> collection, Function<T, R> mapper) {
    return collection.stream()
      .map(mapper)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  public static <T, R> Collection<R> map(Collection<T> collection, Function<T, R> mapper) {
    return collection.stream()
      .map(mapper)
      .collect(Collectors.toList());
  }

  public static <K, V> Function<Collection<V>, Map<K, V>> toMap(Function<V, K> key) {
    return collection -> toMap(collection, key);
  }

  private static <K, V> Map<K, V> toMap(Collection<V> collection, Function<V, K> key) {
    return collection.stream()
      .collect(Collectors.toMap(key, Function.identity()));
  }
}
