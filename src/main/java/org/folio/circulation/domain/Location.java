package org.folio.circulation.domain;

import java.util.List;
import java.util.stream.Collectors;

import org.folio.circulation.support.JsonPropertyFetcher;

import io.vertx.core.json.JsonObject;

public class Location {

  private JsonObject locationRepresentation;

  public Location(JsonObject locationRepresentation) {
    this.locationRepresentation = locationRepresentation;
  }

  public static Location from(JsonObject locationRepresentation) {
    return new Location(locationRepresentation);
  }

  public String getId() {
    return JsonPropertyFetcher.getProperty(locationRepresentation, "id");
  }

  public List<String> getServicePointIds() {
    return JsonPropertyFetcher.getArrayProperty(locationRepresentation, "servicePointIds")
      .stream()
      .map(String.class::cast)
      .collect(Collectors.toList());
  }

  public String getPrimaryServicePoint() {
    return JsonPropertyFetcher.getProperty(locationRepresentation, "primaryServicePoint");
  }

  public String getName() {
    return JsonPropertyFetcher.getProperty(locationRepresentation, "name");
  }
}
