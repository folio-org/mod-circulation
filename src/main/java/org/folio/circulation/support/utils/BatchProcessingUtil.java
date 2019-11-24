package org.folio.circulation.support.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BatchProcessingUtil {

  private BatchProcessingUtil() {
    throw new UnsupportedOperationException();
  }

  public static <T> List<List<T>> partitionList(List<T> list, int batchSize) {
    int size = list.size();
    if (size <= 0) {
      return new ArrayList<>();
    }

    int fullChunks = (size - 1) / batchSize;
    return IntStream.range(0, fullChunks + 1)
        .mapToObj(n ->
            list.subList(n * batchSize, n == fullChunks
                ? size
                : (n + 1) * batchSize))
        .collect(Collectors.toList());
  }
}
