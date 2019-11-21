package org.folio.circulation.support;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonStringArrayHelper {
  private JsonStringArrayHelper() { }

  public static Stream<String> toStream(
    JsonObject within,
    String arrayPropertyName) {

    if(within == null || !within.containsKey(arrayPropertyName)) {
      return Stream.empty();
    }

    return toStream(within.getJsonArray(arrayPropertyName));
  }

  public static Stream<String> toStream(JsonArray array) {
    return Optional.ofNullable(array)
      .map(JsonArray::stream)
      .orElse(Stream.empty())
      .filter(Objects::nonNull)
      .map(castToString())
      .filter(Objects::nonNull);
  }

  private static Function<Object, String> castToString() {
    return entry -> {
      if(entry instanceof String) {
        return (String)entry;
      }
      else {
        return null;
      }
    };
  }

  public static List<String> toList(JsonArray array) {
    return  toStream(array).collect(Collectors.toList());
  }
}
