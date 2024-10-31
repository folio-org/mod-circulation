package org.folio.circulation.domain;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.NonNull;
import lombok.Value;

@Value
public class Location {
  String id;
  String name;
  String code;
  String discoveryDisplayName;
  @NonNull Collection<UUID> servicePointIds;
  UUID primaryServicePointId;
  Boolean isFloatingCollection;
  @NonNull Institution institution;
  @NonNull Campus campus;
  @NonNull Library library;
  ServicePoint primaryServicePoint;

  public static Location unknown() {
    return unknown(null);
  }

  public static Location unknown(String id) {
    return new Location(id, null, null, null, List.of(), null,
      false,
      Institution.unknown(null), Campus.unknown(null), Library.unknown(null),
      ServicePoint.unknown());
  }

  public String getId() {
    return id;
  }

  private Collection<UUID> getServicePointIds() {
    return servicePointIds;
  }

  public UUID getPrimaryServicePointId() {
    return primaryServicePointId;
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

  public boolean isFloatingCollection() {
    return isFloatingCollection;
  }

  public Location withInstitution(Institution institution) {
    return new Location(id, name, code, discoveryDisplayName, servicePointIds, primaryServicePointId,
      isFloatingCollection,
      institution, campus, library, primaryServicePoint);
  }

  public Location withCampus(Campus campus) {
    return new Location(id, name, code, discoveryDisplayName, servicePointIds, primaryServicePointId,
      isFloatingCollection,
      institution, campus, library, primaryServicePoint);
  }

  public Location withLibrary(Library library) {
    return new Location(id, name, code, discoveryDisplayName, servicePointIds, primaryServicePointId,
      isFloatingCollection,
      institution, campus, library, primaryServicePoint);
  }

  public Location withPrimaryServicePoint(ServicePoint servicePoint) {
    return new Location(id, name, code, discoveryDisplayName, servicePointIds, primaryServicePointId,
      isFloatingCollection,
      institution, campus, library, servicePoint);
  }

  private boolean matchesPrimaryServicePoint(UUID servicePointId) {
    return Objects.equals(getPrimaryServicePointId(), servicePointId);
  }

  private boolean matchesAnyServingServicePoint(UUID servicePointId) {
    return getServicePointIds().stream()
      .anyMatch(otherServicePointId -> Objects.equals(servicePointId, otherServicePointId));
  }

  @Override
  public String toString() {
    return String.format("Institution: `%s`, Campus: `%s`, Library: `%s` Location: `%s`",
      getInstitutionId(), getCampusId(), getLibraryId(), getId());
  }
}
