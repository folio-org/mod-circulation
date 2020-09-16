package org.folio.circulation.support.json;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;

import java.util.stream.Stream;

import io.vertx.core.json.JsonObject;

public class JsonStringArrayPropertyFetcher {
  private static final JsonArrayToStreamMapper<String> streamMapper
    = new JsonArrayToStreamMapper<>(JsonStringArrayPropertyFetcher::castToString);

  private JsonStringArrayPropertyFetcher() { }

  public static Stream<String> toStream(JsonObject within, String arrayPropertyName) {
    return streamMapper.toStream(getArrayProperty(within, arrayPropertyName));
  }

  private static String castToString(Object entry) {
    return entry instanceof String
      ? (String) entry
      : null;
  }
}
