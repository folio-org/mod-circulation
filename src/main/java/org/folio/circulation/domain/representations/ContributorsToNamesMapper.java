package org.folio.circulation.domain.representations;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ContributorsToNamesMapper {
  private ContributorsToNamesMapper() { }

  public static JsonArray mapContributorsToNamesOnly(Stream<JsonObject> contributors) {
    return new JsonArray(contributors
      .map(contributor -> new JsonObject().put("name", contributor.getString("name")))
      .collect(Collectors.toList()));
  }
}
