package org.folio.circulation.domain.representations;

import java.util.stream.Collectors;

import org.folio.circulation.domain.Item;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ContributorsToNamesMapper {
  private ContributorsToNamesMapper() { }

  public static JsonArray mapContributorNamesToJson(Item item) {
    return new JsonArray(item.getContributorNames()
      .map(name -> new JsonObject().put("name", name))
      .collect(Collectors.toList()));
  }
}
