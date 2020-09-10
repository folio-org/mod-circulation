package org.folio.circulation.support.json;

import static java.util.function.Function.identity;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonObjectArrayPropertyFetcher {
  private static final JsonArrayPropertyFetcher<JsonObject> streamMapper
    = new JsonArrayPropertyFetcher<>(JsonObjectArrayPropertyFetcher::castToJsonObject);

  private JsonObjectArrayPropertyFetcher() { }

  public static List<JsonObject> toList(JsonArray array) {
    return toStream(array)
      .collect(Collectors.toList());
  }

  public static <T> List<T> mapToList(JsonObject within, String arrayPropertyName,
      Function<JsonObject, T> mapper) {

    return toStream(within, arrayPropertyName)
      .map(mapper)
      .collect(Collectors.toList());
  }

  public static List<JsonObject> mapToList(JsonObject within, String arrayPropertyName) {
    return mapToList(within, arrayPropertyName, identity());
  }

  public static Stream<JsonObject> toStream(JsonArray array) {
    return streamMapper.toStream(array);
  }

  public static Stream<JsonObject> toStream(JsonObject within, String arrayPropertyName) {
    return streamMapper.toStream(within, arrayPropertyName);
  }

  private static JsonObject castToJsonObject(Object entry) {
    return entry instanceof JsonObject
      ? (JsonObject) entry
      : null;
  }
}
