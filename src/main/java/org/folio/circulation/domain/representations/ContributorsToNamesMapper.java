package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.utils.LogUtil.collectionAsString;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Contributor;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ContributorsToNamesMapper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private ContributorsToNamesMapper() { }

  public static JsonArray mapContributorNamesToJson(Item item) {
    log.debug("mapContributorNamesToJson:: parameters item: {}", item);
    return mapContributorNamesToJson(item.getContributorNames());
  }

  public static JsonArray mapContributorNamesToJson(Instance instance) {
    log.debug("mapContributorNamesToJson:: parameters instance: {}", instance);
    return mapContributorNamesToJson(instance.getContributorNames());
  }

  public static JsonArray mapContributorNamesToJson(Collection<Contributor> contributors) {
    log.debug("mapContributorNamesToJson:: parameters contributors: {}",
      () -> collectionAsString(contributors));
    return mapContributorNamesToJson(contributors.stream().map(Contributor::getName));
  }

  public static JsonArray mapContributorNamesToJson(Stream<String> contributorNames) {
    return new JsonArray(contributorNames
      .map(name -> new JsonObject().put("name", name))
      .collect(Collectors.toList()));
  }
}
