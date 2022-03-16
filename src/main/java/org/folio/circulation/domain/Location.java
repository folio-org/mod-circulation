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
  private final String id;
  private final String name;
  private final String code;
  private final JsonObject representation;
  private final @NonNull Institution institution;
  private final @NonNull Campus campus;
  private final @NonNull Library library;
  private final ServicePoint primaryServicePoint;

  public Location(String id, String name, String code, JsonObject representation,
    @NonNull Institution institution, @NonNull Campus campus,
    @NonNull Library library, ServicePoint primaryServicePoint) {

    this.id = id;
    this.name = name;
    this.code = code;
    this.representation = representation;
    this.institution = institution;
    this.campus = campus;
    this.library = library;
    this.primaryServicePoint = primaryServicePoint;
  }

  public String getId() {
    return id;
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
    return name;
  }

  public String getLibraryId() {
    return library.getId();
  }

  public String getCampusId() {
    return campus.getId();
  }

  public String getInstitutionId() {
    return institution.getId();
  }

  public String getCode() {
    return code;
  }

  public String getLibraryName() {
    return library.getName();
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

  public Location withInstitution(Institution institution) {
    return new Location(id, name, code, representation, institution, campus, library,
      primaryServicePoint);
  }

  public Location withCampus(Campus campus) {
    return new Location(id, name, code, representation, institution, campus, library,
      primaryServicePoint);
  }

  public Location withLibrary(Library library) {
    return new Location(id, name, code, representation, institution, campus, library,
      primaryServicePoint);
  }

  public Location withPrimaryServicePoint(ServicePoint servicePoint) {
    return new Location(id, name, code, representation, institution, campus, library,
      servicePoint);
  }

  private boolean matchesPrimaryServicePoint(UUID servicePointId) {
    return Objects.equals(getPrimaryServicePointId(), servicePointId);
  }

  private boolean matchesAnyServingServicePoint(UUID servicePointId) {
    return getServicePointIds().stream()
      .map(UUID::fromString)
      .anyMatch(otherServicePointId -> Objects.equals(servicePointId, otherServicePointId));
  }

  @Override
  public String toString() {
    return String.format("Institution: `%s`, Campus: `%s`, Library: `%s` Location: `%s`",
      getInstitutionId(), getCampusId(), getLibraryId(), getId());
  }
}
