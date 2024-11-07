package api.support.examples;

import static java.util.stream.Stream.of;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import api.support.builders.LocationBuilder;

public class LocationExamples {

  private final UUID djanoglyLibraryId;
  private final UUID institutionId;
  private final UUID jubileeCampusId;
  private final UUID businessLibraryId;
  private final UUID primaryServicePointId;
  private final Set<UUID> otherServicePointIds;

  public LocationExamples(
    UUID institutionId,
    UUID jubileeCampusId,
    UUID businessLibraryId,
    UUID djanoglyLibraryId,
    UUID primaryServicePointId,
    UUID... servicePointIds) {

    this.institutionId = institutionId;
    this.jubileeCampusId = jubileeCampusId;
    this.businessLibraryId = businessLibraryId;
    this.djanoglyLibraryId = djanoglyLibraryId;
    this.primaryServicePointId = primaryServicePointId;

    this.otherServicePointIds = of(servicePointIds)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  public LocationBuilder mezzanineDisplayCase() {
    return new LocationBuilder()
      .withName("Display Case, Mezzanine")
      .withCode("NU/JC/BL/DM")
      .withDiscoveryDisplayName("Mezzanine Display Case Display Name")
      .forInstitution(institutionId)
      .forCampus(jubileeCampusId)
      .forLibrary(businessLibraryId)
      .withPrimaryServicePoint(primaryServicePointId)
      .servedBy(otherServicePointIds);
  }

  public LocationBuilder secondFloorEconomics() {
    return new LocationBuilder()
      .withName("2nd Floor - Economics")
      .withCode("NU/JC/DL/2FE")
      .withDiscoveryDisplayName("2nd Floor - Economics Display Name")
      .forInstitution(institutionId)
      .forCampus(jubileeCampusId)
      .forLibrary(djanoglyLibraryId)
      .withPrimaryServicePoint(primaryServicePointId)
      .servedBy(otherServicePointIds);
  }

  public LocationBuilder thirdFloor() {
    return new LocationBuilder()
      .withName("3rd Floor")
      .withCode("NU/JC/DL/3F")
      .withDiscoveryDisplayName("3rd Floor Display Name")
      .forInstitution(institutionId)
      .forCampus(jubileeCampusId)
      .forLibrary(djanoglyLibraryId)
      .withPrimaryServicePoint(primaryServicePointId)
      .withEffectiveLocationPrimaryServicePointName("Circ Desk 1")
      .servedBy(otherServicePointIds);
  }

  public LocationBuilder example() {
    return new LocationBuilder()
      .withName("Example location")
      .withCode("NU/JC/DL/EX")
      .withDiscoveryDisplayName("Example location Display Name")
      .forInstitution(institutionId)
      .forCampus(jubileeCampusId)
      .forLibrary(djanoglyLibraryId)
      .withPrimaryServicePoint(primaryServicePointId)
      .servedBy(otherServicePointIds);
  }

  public LocationBuilder mainLocation() {
      return new LocationBuilder()
      .withName("Main")
      .withCode("NU/JC/DL/3F")
      .withDiscoveryDisplayName("Main Display Name")
      .forInstitution(institutionId)
      .forCampus(jubileeCampusId)
      .forLibrary(djanoglyLibraryId)
      .withPrimaryServicePoint(primaryServicePointId)
      .servedBy(otherServicePointIds);
  }

  public LocationBuilder fourthFloorLocation() {
    return new LocationBuilder()
      .withName("Fourth Floor")
      .withCode("NU/JC/DL/4F")
      .withDiscoveryDisplayName("Fourth Floor Display Name")
      .forInstitution(institutionId)
      .forCampus(jubileeCampusId)
      .forLibrary(djanoglyLibraryId)
      .withPrimaryServicePoint(primaryServicePointId)
      .servedBy(otherServicePointIds);
  }

  public LocationBuilder floatingCollection() {
    return new LocationBuilder()
      .withName("Floating collection")
      .withCode("NU/JC/DL/FC")
      .withDiscoveryDisplayName("Floating collection")
      .forInstitution(institutionId)
      .forCampus(jubileeCampusId)
      .forLibrary(djanoglyLibraryId)
      .withPrimaryServicePoint(primaryServicePointId)
      .isFloatingCollection(true);
  }

}
