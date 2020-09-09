package org.folio.circulation.support.json;

import static java.util.Optional.ofNullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonStringArrayPropertyFetcher {
  private JsonStringArrayPropertyFetcher() { }

  public static List<String> toList(JsonArray array) {
    return  toStream(array).collect(Collectors.toList());
  }

  public static Stream<String> toStream(JsonObject within, String arrayPropertyName) {
    if (within == null || !within.containsKey(arrayPropertyName)) {
      return Stream.empty();
    }

    return toStream(within.getJsonArray(arrayPropertyName));
  }

  public static Stream<String> toStream(JsonArray array) {
    return ofNullable(array)
      .map(JsonArray::stream)
      .orElse(Stream.empty())
      .filter(Objects::nonNull)
      .map(castToString())
      .filter(Objects::nonNull);
  }

  private static Function<Object, String> castToString() {
    return entry -> {
      if (entry instanceof String) {
        return (String)entry;
      }
      else {
        return null;
      }
    };
  }
}
