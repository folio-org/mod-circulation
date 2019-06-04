package api.support.examples;

import java.util.HashSet;
import java.util.UUID;

import api.support.builders.LocationBuilder;

public class LocationExamples {

  private final UUID djanoglyLibraryId;
  private final UUID nottinghamUniversityId;
  private final UUID jubileeCampusId;
  private final UUID businessLibraryId;
  private final UUID primaryServicePointId;
  private final UUID secondaryServicePointId;
  private final UUID tertiaryServicePointId;

  public LocationExamples(
    UUID nottinghamUniversityId,
    UUID jubileeCampusId,
    UUID businessLibraryId,
    UUID djanoglyLibraryId,
    UUID primaryServicePointId,
    UUID secondaryServicePointId,
    UUID tertiaryServicePointId) {

    this.nottinghamUniversityId = nottinghamUniversityId;
    this.jubileeCampusId = jubileeCampusId;
    this.businessLibraryId = businessLibraryId;
    this.djanoglyLibraryId = djanoglyLibraryId;
    this.primaryServicePointId = primaryServicePointId;
    this.secondaryServicePointId = secondaryServicePointId;
    this.tertiaryServicePointId = tertiaryServicePointId;
  }

  public LocationBuilder mezzanineDisplayCase() {
    return new LocationBuilder()
      .withName("Display Case, Mezzanine")
      .withCode("NU/JC/BL/DM")
      .forInstitution(nottinghamUniversityId)
      .forCampus(jubileeCampusId)
      .forLibrary(businessLibraryId)
      .withPrimaryServicePoint(primaryServicePointId)
      .withServicePoints(getServicePointsList());
  }

  public LocationBuilder secondFloorEconomics() {
    return new LocationBuilder()
      .withName("2nd Floor - Economics")
      .withCode("NU/JC/DL/2FE")
      .forInstitution(nottinghamUniversityId)
      .forCampus(jubileeCampusId)
      .forLibrary(djanoglyLibraryId)
      .withPrimaryServicePoint(primaryServicePointId)
      .withServicePoints(getServicePointsList());
  }

  public LocationBuilder thirdFloor() {
    return new LocationBuilder()
      .withName("3rd Floor")
      .withCode("NU/JC/DL/3F")
      .forInstitution(nottinghamUniversityId)
      .forCampus(jubileeCampusId)
      .forLibrary(djanoglyLibraryId)
      .withPrimaryServicePoint(primaryServicePointId)
      .withServicePoints(getServicePointsList());
  }

  public LocationBuilder fourthFloor(UUID primaryServicePointId) {
    return new LocationBuilder()
      .withName("4th Floor")
      .withCode("NU/JC/DL/3F")
      .forInstitution(nottinghamUniversityId)
      .forCampus(jubileeCampusId)
      .forLibrary(djanoglyLibraryId)
      .withPrimaryServicePoint(primaryServicePointId)
      .withServicePoints(getServicePointsList(primaryServicePointId));
  }

  public LocationBuilder example() {
    return new LocationBuilder()
      .withName("Example location")
      .withCode("NU/JC/DL/EX")
      .forInstitution(nottinghamUniversityId)
      .forCampus(jubileeCampusId)
      .forLibrary(djanoglyLibraryId)
      .withPrimaryServicePoint(primaryServicePointId)
      .withServicePoints(getServicePointsList());
  }

  public LocationBuilder mainLocation() {
      return new LocationBuilder()
      .withName("Main")
      .withCode("NU/JC/DL/3F")
      .forInstitution(nottinghamUniversityId)
      .forCampus(jubileeCampusId)
      .forLibrary(djanoglyLibraryId)
      .withPrimaryServicePoint(primaryServicePointId)
      .withServicePoints(getServicePointsList());
  }

  private HashSet<UUID> getServicePointsList(){
    HashSet<UUID> servicePoints = new HashSet<>();
    servicePoints.add(primaryServicePointId);
    if (secondaryServicePointId != null)
      servicePoints.add(secondaryServicePointId);
    if (tertiaryServicePointId != null)
      servicePoints.add(tertiaryServicePointId);

    return servicePoints;
  }

  private HashSet<UUID> getServicePointsList(UUID servicePointId){
    HashSet<UUID> servicePoints = new HashSet<>();
    servicePoints.add(primaryServicePointId);
    if (servicePointId != null)
      servicePoints.add(servicePointId);

    return servicePoints;
  }
}
