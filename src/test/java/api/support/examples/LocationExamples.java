package api.support.examples;

import static api.APITestSuite.businessLibrary;
import static api.APITestSuite.djanoglyLibrary;
import static api.APITestSuite.jubileeCampus;
import static api.APITestSuite.nottinghamUniversityInstitution;

import java.util.UUID;

import api.APITestSuite;
import api.support.builders.LocationBuilder;

public class LocationExamples {
  private LocationExamples() { }

  public static LocationBuilder mezzanineDisplayCase(UUID primaryServicePointId) {
    return new LocationBuilder()
      .withName("Display Case, Mezzanine")
      .withCode("NU/JC/BL/DM")
      .forInstitution(nottinghamUniversityInstitution())
      .forCampus(jubileeCampus())
      .forLibrary(businessLibrary())
      .withPrimaryServicePoint(primaryServicePointId);
  }

  public static LocationBuilder secondFloorEconomics(UUID primaryServicePointId) {
    return new LocationBuilder()
      .withName("2nd Floor - Economics")
      .withCode("NU/JC/DL/2FE")
      .forInstitution(nottinghamUniversityInstitution())
      .forCampus(jubileeCampus())
      .forLibrary(APITestSuite.djanoglyLibrary())
      .withPrimaryServicePoint(primaryServicePointId);
  }

  public static LocationBuilder thirdFloor(UUID primaryServicePointId) {
    return new LocationBuilder()
      .withName("3rd Floor")
      .withCode("NU/JC/DL/3F")
      .forInstitution(nottinghamUniversityInstitution())
      .forCampus(jubileeCampus())
      .forLibrary(APITestSuite.djanoglyLibrary())
      .withPrimaryServicePoint(primaryServicePointId);
  }

  public static LocationBuilder exampleLocation(UUID primaryServicePointId) {
    return new LocationBuilder()
      .withName("Example location")
      .withCode("NU/JC/DL/EX")
      .forInstitution(nottinghamUniversityInstitution())
      .forCampus(jubileeCampus())
      .forLibrary(djanoglyLibrary())
      .withPrimaryServicePoint(primaryServicePointId);
  }
}
