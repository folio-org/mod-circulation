package org.folio.circulation.domain.representations;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.circulation.domain.Contributor;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ContributorsToNamesMapper {
  private ContributorsToNamesMapper() { }

  public static JsonArray mapContributorsToNamesOnly(Stream<Contributor> contributors) {
    return new JsonArray(contributors
      .map(Contributor::getName)
      .filter(Objects::nonNull)
      .map(name -> new JsonObject().put("name", name))
      .collect(Collectors.toList()));
  }
}
