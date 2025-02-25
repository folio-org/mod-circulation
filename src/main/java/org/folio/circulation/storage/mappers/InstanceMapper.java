package org.folio.circulation.storage.mappers;

import static org.folio.circulation.storage.mappers.IdentifierMapper.mapIdentifiers;
import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.mapToList;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.List;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Contributor;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Publication;

import io.vertx.core.json.JsonObject;

public class InstanceMapper {
  public Instance toDomain(JsonObject representation) {
    return new Instance(getProperty(representation, "id"),
      getProperty(representation, "hrid"),
      getProperty(representation, "title"),
      mapIdentifiers(representation), mapContributors(representation),
      mapPublication(representation), mapEditions(representation),
      mapPhysicalDescriptions(representation));
  }

  private List<Contributor> mapContributors(JsonObject representation) {
    return mapToList(representation, "contributors", new ContributorMapper()::toDomain);
  }

  private List<Publication> mapPublication(JsonObject representation) {
    return mapToList(representation, "publication", new PublicationMapper()::toDomain);
  }

  private List<String> mapEditions(JsonObject representation) {
    return getArrayProperty(representation, "editions")
      .stream()
      .map(String.class::cast)
      .collect(Collectors.toList());
  }

  private List<String> mapPhysicalDescriptions(JsonObject representation) {
    return getArrayProperty(representation, "physicalDescriptions")
      .stream()
      .map(String.class::cast)
      .collect(Collectors.toList());
  }

}
