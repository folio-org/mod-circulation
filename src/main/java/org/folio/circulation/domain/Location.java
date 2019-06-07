package org.folio.circulation.domain;

import java.util.List;
import java.util.stream.Collectors;

import org.folio.circulation.support.JsonPropertyFetcher;

import io.vertx.core.json.JsonObject;

public class Location {

  private final JsonObject representation;
  private final JsonObject libraryRepresentation;
  private final JsonObject campusRepresentation;
  private final JsonObject institutionRepresentation;

  public Location(JsonObject representation, JsonObject libraryRepresentation, JsonObject campusRepresentation, JsonObject institutionRepresentation) {
    this.representation = representation;
    this.libraryRepresentation = libraryRepresentation;
    this.campusRepresentation = campusRepresentation;
    this.institutionRepresentation = institutionRepresentation;
  }

  public static Location from(JsonObject locationRepresentation) {
    return new Location(locationRepresentation, null, null, null);
  }

  public String getId() {
    return JsonPropertyFetcher.getProperty(representation, "id");
  }

  public List<String> getServicePointIds() {
    return JsonPropertyFetcher.getArrayProperty(representation, "servicePointIds")
      .stream()
      .map(String.class::cast)
      .collect(Collectors.toList());
  }

  public String getPrimaryServicePoint() {
    return JsonPropertyFetcher.getProperty(representation, "primaryServicePoint");
  }

  public String getName() {
    return JsonPropertyFetcher.getProperty(representation, "name");
  }

  public String getLibraryId() {
    return JsonPropertyFetcher.getProperty(representation, "libraryId");
  }

  public String getCampusId() {
    return JsonPropertyFetcher.getProperty(representation, "campusId");
  }

  public String getInstitutionId() {
    return JsonPropertyFetcher.getProperty(representation, "institutionId");
  }

  public String getLibraryName() {
    return JsonPropertyFetcher.getProperty(libraryRepresentation, "name");
  }

  public String getCampusName() {
    return JsonPropertyFetcher.getProperty(campusRepresentation, "name");
  }

  public String getInstitutionName() {
    return JsonPropertyFetcher.getProperty(institutionRepresentation, "name");
  }

  public Location withLibraryRepresentation(JsonObject libraryRepresentation) {
    return new Location(representation, libraryRepresentation, campusRepresentation, institutionRepresentation);
  }

  public Location withCampusRepresentation(JsonObject campusRepresentation) {
    return new Location(representation, libraryRepresentation, campusRepresentation, institutionRepresentation);
  }

  public Location withInstitutionRepresentation(JsonObject institutionRepresentation) {
    return new Location(representation, libraryRepresentation, campusRepresentation, institutionRepresentation);
  }
}
