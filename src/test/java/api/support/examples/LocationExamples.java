package api.support.examples;

import java.util.UUID;

import api.support.builders.LocationBuilder;

public class LocationExamples {

  private final UUID djanoglyLibraryId;
  private final UUID nottinghamUniversityId;
  private final UUID jubileeCampusId;
  private final UUID businessLibraryId;
  private final UUID primaryServicePointId;

  public LocationExamples(
    UUID nottinghamUniversityId,
    UUID jubileeCampusId,
    UUID businessLibraryId,
    UUID djanoglyLibraryId,
    UUID primaryServicePointId) {

    this.nottinghamUniversityId = nottinghamUniversityId;
    this.jubileeCampusId = jubileeCampusId;
    this.businessLibraryId = businessLibraryId;
    this.djanoglyLibraryId = djanoglyLibraryId;
    this.primaryServicePointId = primaryServicePointId;
  }

  public LocationBuilder mezzanineDisplayCase() {
    return new LocationBuilder()
      .withName("Display Case, Mezzanine")
      .withCode("NU/JC/BL/DM")
      .withLibraryName("Radioelectronic Institut")
      .forInstitution(nottinghamUniversityId)
      .forCampus(jubileeCampusId)
      .forLibrary(businessLibraryId)
      .withPrimaryServicePoint(primaryServicePointId);
  }

  public LocationBuilder secondFloorEconomics() {
    return new LocationBuilder()
      .withName("2nd Floor - Economics")
      .withCode("NU/JC/DL/2FE")
      .withLibraryName("Datalogisk Institut")
      .forInstitution(nottinghamUniversityId)
      .forCampus(jubileeCampusId)
      .forLibrary(djanoglyLibraryId)
      .withPrimaryServicePoint(primaryServicePointId);
  }

  public LocationBuilder thirdFloor() {
    return new LocationBuilder()
      .withName("3rd Floor")
      .withCode("NU/JC/DL/3F")
      .forInstitution(nottinghamUniversityId)
      .forCampus(jubileeCampusId)
      .forLibrary(djanoglyLibraryId)
      .withPrimaryServicePoint(primaryServicePointId);
  }

  public LocationBuilder example() {
    return new LocationBuilder()
      .withName("Example location")
      .withCode("NU/JC/DL/EX")
      .forInstitution(nottinghamUniversityId)
      .forCampus(jubileeCampusId)
      .forLibrary(djanoglyLibraryId)
      .withPrimaryServicePoint(primaryServicePointId);
  }
}
