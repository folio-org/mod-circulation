package org.folio.circulation.support;

import java.util.Objects;
import java.util.function.Function;
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
    return array
      .stream()
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
}
