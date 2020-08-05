package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.LocationBuilder;
import api.support.examples.LocationExamples;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class LocationsFixture {
  private final RecordCreator locationRecordCreator;
  private final ServicePointsFixture servicePointsFixture;
  private final RecordCreator institutionRecordCreator;
  private final RecordCreator campusRecordCreator;
  private final RecordCreator libraryRecordCreator;

  public LocationsFixture(
    ResourceClient client,
    ResourceClient institutionsClient,
    ResourceClient campusesClient,
    ResourceClient librariesClient,
    ServicePointsFixture servicePointsFixture) {

    this.locationRecordCreator = new RecordCreator(client,
      location -> getProperty(location, "code"));

    institutionRecordCreator = new RecordCreator(institutionsClient,
      institution -> getProperty(institution, "name"));

    campusRecordCreator = new RecordCreator(campusesClient,
      campus -> getProperty(campus, "name"));

    libraryRecordCreator = new RecordCreator(librariesClient,
      library -> getProperty(library, "name"));

    this.servicePointsFixture = servicePointsFixture;
  }

  public void cleanUp() {

    locationRecordCreator.cleanUp();

    libraryRecordCreator.cleanUp();
    campusRecordCreator.cleanUp();
    institutionRecordCreator.cleanUp();
  }

  public IndividualResource basedUponExampleLocation(
    Function<LocationBuilder, LocationBuilder> additionalLocationProperties) {

    final LocationExamples locationExamples = getLocationExamples();

    return locationRecordCreator.createIfAbsent(
      additionalLocationProperties.apply(locationExamples.example()));
  }

  public IndividualResource thirdFloor() {

    final LocationExamples locationExamples = getLocationExamples();

    return locationRecordCreator.createIfAbsent(
      locationExamples.thirdFloor());
  }
  public IndividualResource fourthServicePoint() {

    final LocationExamples locationExamples = getLocationExamplesForCd4();

    return locationRecordCreator.createIfAbsent(
        locationExamples.secondFloorEconomics());
  }

  public IndividualResource secondFloorEconomics() {

    final LocationExamples locationExamples = getLocationExamples();

    return locationRecordCreator.createIfAbsent(
      locationExamples.secondFloorEconomics());
  }

  public IndividualResource mezzanineDisplayCase() {

    final LocationExamples locationExamples = getLocationExamples();

    return locationRecordCreator.createIfAbsent(
      locationExamples.mezzanineDisplayCase());
  }

  /**
  mainFloor location has multiple service points:
      servicePointsFixture.cd1()  is primary service point,
      servicePointsFixture.cd2(),
      servicePointsFixture.cd3(),
  */
  public IndividualResource mainFloor() {

    final LocationExamples locationExamples = getLocationMultiServicePointsExamples();

    return locationRecordCreator.createIfAbsent(locationExamples.mainLocation());
  }

  public IndividualResource fourthFloor() {

    final LocationExamples locationExamples = getLocationExamplesWithKopenhavnInstitution();

    return locationRecordCreator.createIfAbsent(locationExamples.fourthFloorLocation());
  }

  private LocationExamples getLocationExamples() {

    return new LocationExamples(
      nottinghamUniversity().getId(),
      jubileeCampus().getId(),
      businessLibrary().getId(),
      djanoglyLibrary().getId(),
      servicePointsFixture.cd1().getId(),
      null,
      null);
  }

  private LocationExamples getLocationExamplesForCd4() {

    return new LocationExamples(
        nottinghamUniversity().getId(),
        jubileeCampus().getId(),
        businessLibrary().getId(),
        djanoglyLibrary().getId(),
        servicePointsFixture.cd4().getId(),
        null,
        null);
  }

  private LocationExamples getLocationExamplesWithKopenhavnInstitution() {

    return new LocationExamples(
      kopenhavnUniversity().getId(),
      mainCampus().getId(),
      mainLibrary().getId(),
      mainLibrary().getId(),
      servicePointsFixture.cd6().getId(),
      null,
      null);
  }

  private LocationExamples getLocationMultiServicePointsExamples() {

    return new LocationExamples(
      nottinghamUniversity().getId(),
      jubileeCampus().getId(),
      businessLibrary().getId(),
      djanoglyLibrary().getId(),
      servicePointsFixture.cd1().getId(),
      servicePointsFixture.cd2().getId(),
      servicePointsFixture.cd3().getId());
  }

  private IndividualResource djanoglyLibrary() {

    final JsonObject djanoglyLibrary = new JsonObject();

    write(djanoglyLibrary, "name", "Djanogly Learning Resource Centre");
    write(djanoglyLibrary, "campusId", jubileeCampus().getId());
    write(djanoglyLibrary, "code", "DLRC");

    return libraryRecordCreator.createIfAbsent(djanoglyLibrary);
  }

  private IndividualResource businessLibrary() {

    final JsonObject businessLibrary = new JsonObject();

    write(businessLibrary, "name", "Business Library");
    write(businessLibrary, "campusId", jubileeCampus().getId());
    write(businessLibrary, "code", "BL");

    return libraryRecordCreator.createIfAbsent(businessLibrary);
  }

  private IndividualResource mainLibrary() {

    final JsonObject businessLibrary = new JsonObject();

    write(businessLibrary, "name", "Main Library");
    write(businessLibrary, "campusId", mainCampus().getId());
    write(businessLibrary, "code","ML");

    return libraryRecordCreator.createIfAbsent(businessLibrary);
  }

  private IndividualResource jubileeCampus() {

    final JsonObject jubileeCampus = new JsonObject();

    write(jubileeCampus, "name", "Jubilee Campus");
    write(jubileeCampus, "institutionId", nottinghamUniversity().getId());
    write(jubileeCampus, "code", "JC");

    return campusRecordCreator.createIfAbsent(jubileeCampus);
  }

  private IndividualResource mainCampus() {

    final JsonObject mainCampus = new JsonObject();

    write(mainCampus, "name", "Main Campus");
    write(mainCampus, "institutionId", kopenhavnUniversity().getId());
    write(mainCampus, "code", "MC");

    return campusRecordCreator.createIfAbsent(mainCampus);
  }

  private IndividualResource nottinghamUniversity() {

    final JsonObject nottinghamUniversity = new JsonObject();

    write(nottinghamUniversity, "name", "Nottingham University");
    write(nottinghamUniversity, "code", "NU");

    return institutionRecordCreator.createIfAbsent(nottinghamUniversity);
  }

  private IndividualResource kopenhavnUniversity() {

    final JsonObject kopenhavnUniversity = new JsonObject();

    write(kopenhavnUniversity, "name", "Kopenhavn University");
    write(kopenhavnUniversity, "code", "KU");

    return institutionRecordCreator.createIfAbsent(kopenhavnUniversity);
  }
}
