package org.folio.circulation.support.json;

import static java.util.stream.Stream.empty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonArrayPropertyFetcher <T> {
  private final Function<Object, T> entryMapper;

  JsonArrayPropertyFetcher(Function<Object, T> entryMapper) {
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

  public Stream<T> toStream(JsonObject within, String arrayPropertyName) {
    return toStream(getArrayProperty(within, arrayPropertyName));
  }
}
