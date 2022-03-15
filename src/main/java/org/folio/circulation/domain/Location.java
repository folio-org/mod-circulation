package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonObject;
import lombok.NonNull;

public class Location {
  private final JsonObject representation;
  private final JsonObject libraryRepresentation;
  private final @NonNull Institution institution;
  private final @NonNull Campus campus;
  private final ServicePoint primaryServicePoint;

  public Location(JsonObject representation, JsonObject libraryRepresentation,
    Institution institution, Campus campus, ServicePoint primaryServicePoint) {

    this.representation = representation;
    this.libraryRepresentation = libraryRepresentation;
    this.institution = institution;
    this.campus = campus;
    this.primaryServicePoint = primaryServicePoint;
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

  public String getLibraryId() {
    return getProperty(representation, "libraryId");
  }

  public String getCampusId() {
    return campus.getId();
  }

  public String getInstitutionId() {
    return institution.getId();
  }

  public String getCode() {
    return getProperty(representation, "code");
  }

  public String getLibraryName() {
    return getProperty(libraryRepresentation, "name");
  }

  public String getCampusName() {
    return campus.getName();
  }

  public String getInstitutionName() {
    return institution.getName();
  }

  public ServicePoint getPrimaryServicePoint() {
    return primaryServicePoint;
  }

  public Location withLibraryRepresentation(JsonObject libraryRepresentation) {
    return new Location(representation, libraryRepresentation, institution,
      campus, primaryServicePoint);
  }

  public Location withInstitution(Institution institution) {
    return new Location(representation, libraryRepresentation,
      institution, campus, primaryServicePoint);
  }

  public Location withCampus(Campus campus) {
    return new Location(representation, libraryRepresentation, institution,
      campus, primaryServicePoint);
  }

  public Location withPrimaryServicePoint(ServicePoint servicePoint) {
    return new Location(representation, libraryRepresentation, institution,
      campus, servicePoint);
  }

  private boolean matchesPrimaryServicePoint(UUID servicePointId) {
    return Objects.equals(getPrimaryServicePointId(), servicePointId);
  }

  private boolean matchesAnyServingServicePoint(UUID servicePointId) {
    return getServicePointIds().stream()
      .map(UUID::fromString)
      .anyMatch(id -> Objects.equals(servicePointId, id));
  }

  @Override
  public String toString() {
    return String.format("Institution: `%s`, Campus: `%s`, Library: `%s` Location: `%s`",
      getInstitutionId(), getCampusId(), getLibraryId(), getId());
  }
}
