package org.folio.circulation.domain;

import static org.folio.circulation.domain.representations.InstanceProperties.CONTRIBUTORS;
import static org.folio.circulation.domain.representations.InstanceProperties.EDITIONS;
import static org.folio.circulation.domain.representations.InstanceProperties.PUBLICATION;
import static org.folio.circulation.domain.representations.ItemProperties.IDENTIFIERS;
import static org.folio.circulation.domain.representations.ItemProperties.TITLE;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.stream.Stream;

import org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Instance {
  private final JsonObject instanceRepresentation;

  public static Instance from(JsonObject representation) {
    return new Instance(representation);
  }

  public boolean isNotFound() {
    return !isFound();
  }

  public boolean isFound() {
    return instanceRepresentation != null;
  }

  public String getTitle() {
    return getProperty(instanceRepresentation, TITLE);
  }

  public Stream<JsonObject> getContributors() {
    return JsonObjectArrayPropertyFetcher.toStream(instanceRepresentation, CONTRIBUTORS);
  }

  public JsonArray getIdentifiers() {
    return getArrayProperty(instanceRepresentation, IDENTIFIERS);
  }

  public JsonArray getPublication() {
    return getArrayProperty(instanceRepresentation, PUBLICATION);
  }

  public JsonArray getEditions() {
    return getArrayProperty(instanceRepresentation, EDITIONS);
  }
}
