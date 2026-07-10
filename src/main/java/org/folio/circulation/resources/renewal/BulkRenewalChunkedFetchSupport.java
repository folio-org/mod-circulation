package org.folio.circulation.resources.renewal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class BulkRenewalChunkedFetchSupport {
  public static final int ITEM_ID_CHUNK_SIZE = 80;

  private BulkRenewalChunkedFetchSupport() {
  }

  public static <T> List<List<T>> partition(Collection<T> values, int chunkSize) {
    if (chunkSize <= 0) {
      throw new IllegalArgumentException("chunkSize must be greater than 0");
    }

    if (values == null || values.isEmpty()) {
      return List.of();
    }

    final List<T> filteredValues = values.stream()
      .filter(Objects::nonNull)
      .filter(value -> !isBlankString(value))
      .toList();

    final List<List<T>> partitions = new ArrayList<>();

    for (int index = 0; index < filteredValues.size(); index += chunkSize) {
      partitions.add(filteredValues.subList(index,
        Math.min(index + chunkSize, filteredValues.size())));
    }

    return partitions;
  }

  private static boolean isBlankString(Object value) {
    return value instanceof String stringValue && stringValue.isBlank();
  }
}
