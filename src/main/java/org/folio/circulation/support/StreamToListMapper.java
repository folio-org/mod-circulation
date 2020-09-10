package org.folio.circulation.support;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StreamToListMapper {
  private StreamToListMapper() { }

  public static <T> List<T> toList(Stream<T> stream) {
    if (stream == null) {
      return null;
    }

    return stream.collect(Collectors.toList());
  }
}
