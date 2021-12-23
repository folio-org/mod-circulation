package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.mapToList;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.List;

import org.folio.circulation.domain.Contributor;
import org.folio.circulation.domain.Identifier;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Publication;

import io.vertx.core.json.JsonObject;

public class InstanceMapper {
  public Instance toDomain(JsonObject representation) {
    return new Instance(getProperty(representation, "title"),
      mapIdentifiers(representation), mapContributors(representation),
      mapPublication(representation), mapEditions(representation));
  }

  private List<Identifier> mapIdentifiers(JsonObject representation) {
    return mapToList(representation, "identifiers", new IdentifierMapper()::toDomain);
  }

  private List<Contributor> mapContributors(JsonObject representation) {
    return mapToList(representation, "contributors", new ContributorMapper()::toDomain);
  }

  private List<Publication> mapPublication(JsonObject representation) {
    return mapToList(representation, "publication", new PublicationMapper()::toDomain);
  }

  private List<String> mapEditions(JsonObject representation) {
    return mapToList(representation, "editions",
      res -> getProperty(representation, "editions"));
  }
}
