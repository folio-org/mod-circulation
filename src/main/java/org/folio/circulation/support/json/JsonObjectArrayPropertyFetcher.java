package org.folio.circulation.support.json;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Stream.empty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonObjectArrayPropertyFetcher {
  private JsonObjectArrayPropertyFetcher() { }

  public static List<JsonObject> toList(JsonArray array) {
    if (array == null) {
      return new ArrayList<>();
    }

    return toStream(array)
      .collect(Collectors.toList());
  }

  public static <T> List<T> mapToList(JsonArray array, Function<JsonObject, T> mapper) {
    if (array == null) {
      return emptyList();
    }

    return toStream(array)
      .map(mapper)
      .collect(Collectors.toList());
  }

  public static <T> List<T> mapToList(JsonObject within, String arrayPropertyName,
      Function<JsonObject, T> mapper) {

    if (within == null || !within.containsKey(arrayPropertyName)) {
      return emptyList();
    }

    return mapToList(within.getJsonArray(arrayPropertyName), mapper);
  }

  public static List<JsonObject> mapToList(JsonObject within, String arrayPropertyName) {
    return mapToList(within, arrayPropertyName, identity());
  }

  public static Stream<JsonObject> toStream(JsonObject within, String arrayPropertyName) {
    if (within == null || !within.containsKey(arrayPropertyName)) {
      return empty();
    }

    return toStream(within.getJsonArray(arrayPropertyName));
  }

  public static Stream<JsonObject> toStream(JsonArray array) {
    return array
      .stream()
      .map(castToJsonObject())
      .filter(Objects::nonNull);
  }

  private static Function<Object, JsonObject> castToJsonObject() {
    return entry -> {
      if (entry instanceof JsonObject) {
        return (JsonObject)entry;
      }
      else {
        return null;
      }
    };
  }
}
