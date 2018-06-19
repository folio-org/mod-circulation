package org.folio.circulation.support;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JsonArrayHelper {
  private JsonArrayHelper() { }

  public static List<JsonObject> toList(JsonArray array) {
    if(array == null) {
      return new ArrayList<>();
    }

    return toStream(array)
      .collect(Collectors.toList());
  }

  public static <T> List<T> mapToList(
    JsonArray array,
    Function<JsonObject, T> mapper) {

    if(array == null) {
      return new ArrayList<>();
    }

    return toStream(array)
      .map(mapper)
      .collect(Collectors.toList());
  }

  public static <T> List<T> mapToList(
    JsonObject within,
    String arrayPropertyName,
    Function<JsonObject, T> mapper) {

    if(within == null || !within.containsKey(arrayPropertyName)) {
      return new ArrayList<>();
    }

    return mapToList(within.getJsonArray(arrayPropertyName), mapper);
  }

  public static <T> Stream<T> toStream(
    JsonObject within,
    String arrayPropertyName,
    Function<JsonObject, T> mapper) {

    if(within == null || !within.containsKey(arrayPropertyName)) {
      return null;
    }

    return toStream(within.getJsonArray(arrayPropertyName))
      .map(mapper);
  }

  public static Stream<JsonObject> toStream(
    JsonObject within,
    String arrayPropertyName) {

    if(within == null || !within.containsKey(arrayPropertyName)) {
      return new ArrayList<JsonObject>().stream();
    }

    return toStream(within.getJsonArray(arrayPropertyName));
  }

  private static Stream<JsonObject> toStream(JsonArray array) {
    return array
      .stream()
      .map(castToJsonObject())
      .filter(Objects::nonNull);
  }

  private static Function<Object, JsonObject> castToJsonObject() {
    return loan -> {
      if(loan instanceof JsonObject) {
        return (JsonObject)loan;
      }
      else {
        return null;
      }
    };
  }
}
