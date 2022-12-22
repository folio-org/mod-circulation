package org.folio.circulation.domain.representations;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.circulation.domain.Contributor;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ContributorsToNamesMapper {
  private ContributorsToNamesMapper() { }

  public static JsonArray mapContributorNamesToJson(Item item) {
    return mapContributorNamesToJson(item.getContributorNames());
  }

  public static JsonArray mapContributorNamesToJson(Instance instance) {
    return mapContributorNamesToJson(instance.getContributorNames());
  }

  public static JsonArray mapContributorNamesToJson(Collection<Contributor> contributors) {
    return mapContributorNamesToJson(contributors.stream().map(Contributor::getName));
  }

  public static JsonArray mapContributorNamesToJson(Stream<String> contributorNames) {
    return new JsonArray(contributorNames
      .map(name -> new JsonObject().put("name", name))
      .collect(Collectors.toList()));
  }
}
