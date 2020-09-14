package org.folio.circulation.support.json;

import static org.folio.circulation.support.StreamToListMapper.toList;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import io.vertx.core.json.JsonObject;

public class JsonObjectArrayPropertyFetcher {
  private static final JsonArrayPropertyFetcher<JsonObject> streamMapper
    = new JsonArrayPropertyFetcher<>(JsonObjectArrayPropertyFetcher::castToJsonObject);

  private JsonObjectArrayPropertyFetcher() { }

  public static <T> List<T> mapToList(JsonObject within, String arrayPropertyName,
      Function<JsonObject, T> mapper) {

    return toList(toStream(within, arrayPropertyName).map(mapper));
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
