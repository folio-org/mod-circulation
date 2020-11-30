package org.folio.circulation.support.json;

import static org.folio.circulation.support.StreamToListMapper.toList;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonObjectArrayPropertyFetcher {
  private static final JsonArrayToStreamMapper<JsonObject> streamMapper
    = new JsonArrayToStreamMapper<>(JsonObjectArrayPropertyFetcher::castToJsonObject);

  private JsonObjectArrayPropertyFetcher() { }

  public static <T> List<T> mapToList(JsonArray array, Function<JsonObject, T> mapper) {
    return toList(streamMapper.toStream(array).map(mapper));
  }

  public static <T> List<T> mapToList(JsonObject within, String arrayPropertyName,
      Function<JsonObject, T> mapper) {

    return mapToList(getArrayProperty(within, arrayPropertyName), mapper);
  }

  public static Stream<JsonObject> toStream(JsonObject within, String arrayPropertyName) {
    return streamMapper.toStream(getArrayProperty(within, arrayPropertyName));
  }

  private static JsonObject castToJsonObject(Object entry) {
    return entry instanceof JsonObject
      ? (JsonObject) entry
      : null;
  }
}
