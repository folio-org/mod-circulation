package org.folio.circulation.support.utils;

import static java.util.stream.Collectors.toList;

import java.util.Collection;

import org.folio.circulation.domain.MappableToJson;

import io.vertx.core.json.JsonObject;

public class RequestRepresentationUtil {
  public static Collection<JsonObject> entityCollectionToJson(
    Collection<? extends MappableToJson> mappableToJsons) {
    return mappableToJsons.stream()
      .map(MappableToJson::toJson)
      .collect(toList());
  }
}
