package org.folio.circulation.support.json;

import static java.util.stream.Stream.empty;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;

public class JsonArrayToStreamMapper<T> {
  private final Function<Object, T> entryMapper;

  JsonArrayToStreamMapper(Function<Object, T> entryMapper) {
    this.entryMapper = entryMapper;
  }

  public Stream<T> toStream(JsonArray array) {
    if (array == null) {
      return empty();
    }

    return array
      .stream()
      .map(entryMapper)
      .filter(Objects::nonNull);
  }
}
