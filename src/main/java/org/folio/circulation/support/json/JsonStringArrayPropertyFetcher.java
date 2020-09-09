package org.folio.circulation.support.json;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonStringArrayPropertyFetcher {
  private static final JsonArrayPropertyFetcher<String> streamMapper
    = new JsonArrayPropertyFetcher<>(JsonStringArrayPropertyFetcher::castToString);

  private JsonStringArrayPropertyFetcher() { }

  public static List<String> toList(JsonArray array) {
    return toStream(array).collect(Collectors.toList());
  }

  public static Stream<String> toStream(JsonObject within, String arrayPropertyName) {
    return streamMapper.toStream(within, arrayPropertyName);
  }

  public static Stream<String> toStream(JsonArray array) {
    return streamMapper.toStream(array);
  }

  private static String castToString(Object entry) {
    return entry instanceof String
      ? (String) entry
      : null;
  }
}
