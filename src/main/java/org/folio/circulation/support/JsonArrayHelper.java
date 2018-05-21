package org.folio.circulation.support;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class JsonArrayHelper {
  private JsonArrayHelper() { }

  public static List<JsonObject> toList(JsonArray array) {
    if(array == null) {
      return new ArrayList<>();
    }

    return array
      .stream()
      .map(loan -> {
        if(loan instanceof JsonObject) {
          return (JsonObject)loan;
        }
        else {
          return null;
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }
}
