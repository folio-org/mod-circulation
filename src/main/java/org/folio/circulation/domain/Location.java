package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonObject;

public class Location {

  private final JsonObject representation;
  private final JsonObject libraryRepresentation;
  private final JsonObject campusRepresentation;
  private final JsonObject institutionRepresentation;

  public Location(JsonObject representation,
                  JsonObject libraryRepresentation,
                  JsonObject campusRepresentation,
                  JsonObject institutionRepresentation) {
    this.representation = representation;
    this.libraryRepresentation = libraryRepresentation;
    this.campusRepresentation = campusRepresentation;
    this.institutionRepresentation = institutionRepresentation;
  }

  public static Location from(JsonObject locationRepresentation) {
    return new Location(locationRepresentation, null, null, null);
  }

  public String getId() {
    return getProperty(representation, "id");
  }

  private List<String> getServicePointIds() {
    return getArrayProperty(representation, "servicePointIds")
      .stream()
      .map(String.class::cast)
      .collect(Collectors.toList());
  }

  public UUID getPrimaryServicePointId() {
    return Optional.ofNullable(getProperty(representation, "primaryServicePoint"))
      .map(UUID::fromString)
      .orElse(null);
  }

  public boolean homeLocationIsServedBy(UUID servicePointId) {
    //Defensive check just in case primary isn't part of serving set
    return matchesPrimaryServicePoint(servicePointId) ||
      matchesAnyServingServicePoint(servicePointId);
  }

  public String getName() {
    return getProperty(representation, "name");
  }

  String getLibraryId() {
    return getProperty(representation, "libraryId");
  }

  String getCampusId() {
    return getProperty(representation, "campusId");
  }

  String getInstitutionId() {
    return getProperty(representation, "institutionId");
  }

  String getCode() {
    return getProperty(representation, "code");
  }

  public String getLibraryName() {
    return getProperty(libraryRepresentation, "name");
  }

  public String getCampusName() {
    return getProperty(campusRepresentation, "name");
  }

  public String getInstitutionName() {
    return getProperty(institutionRepresentation, "name");
  }

  Location withLibraryRepresentation(JsonObject libraryRepresentation) {
    return new Location(representation, libraryRepresentation, campusRepresentation, institutionRepresentation);
  }

  Location withCampusRepresentation(JsonObject campusRepresentation) {
    return new Location(representation, libraryRepresentation, campusRepresentation, institutionRepresentation);
  }

  Location withInstitutionRepresentation(JsonObject institutionRepresentation) {
    return new Location(representation, libraryRepresentation, campusRepresentation, institutionRepresentation);
  }

  private boolean matchesPrimaryServicePoint(UUID servicePointId) {
    return Objects.equals(getPrimaryServicePointId(), servicePointId);
  }

  private boolean matchesAnyServingServicePoint(UUID servicePointId) {
    return getServicePointIds().stream()
      .map(UUID::fromString)
      .anyMatch(id -> Objects.equals(servicePointId, id));
  }
}
