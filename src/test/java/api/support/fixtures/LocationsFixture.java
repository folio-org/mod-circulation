package api.support.fixtures;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.Random;
import java.util.UUID;
import java.util.function.Function;

import api.support.http.IndividualResource;

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

  public IndividualResource floatingCollection() {
    final LocationExamples locationExamples = getLocationExamples();

    return locationRecordCreator.createIfAbsent(
      locationExamples.floatingCollection());
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
    return createLibrary("Djanogly Learning Resource Centre", "DLRC", jubileeCampus().getId());
  }

  private IndividualResource businessLibrary() {
    return createLibrary("Business Library", "BL", jubileeCampus().getId());
  }

  private IndividualResource mainLibrary() {
    return createLibrary("Main Library", "ML", mainCampus().getId());
  }

  private IndividualResource jubileeCampus() {
    return createCampus("Jubilee Campus", "JC", nottinghamUniversity().getId());
  }

  private IndividualResource mainCampus() {
    return createCampus("Main Campus", "MC", kopenhavnUniversity().getId());
  }

  private IndividualResource nottinghamUniversity() {
    return createInstitution("Nottingham University", "NU");
  }

  private IndividualResource kopenhavnUniversity() {
    return createInstitution("Kopenhavn University", "KU");
  }

  public IndividualResource createInstitution(String name) {
    return createInstitution(name, randomCode());
  }

  public IndividualResource createInstitution(String name, String code) {
    final JsonObject institution = buildLocationUnitTemplate(name, code);

    return institutionRecordCreator.createIfAbsent(institution);
  }

  public IndividualResource createCampus(String name, UUID institutionId) {
    return createCampus(name, randomCode(), institutionId);
  }

  public IndividualResource createCampus(String name, String code, UUID institutionId) {
    final JsonObject campus = buildLocationUnitTemplate(name, code);
    write(campus, "institutionId", institutionId);

    return campusRecordCreator.createIfAbsent(campus);
  }

  public IndividualResource createLibrary(String name, UUID campusId) {
    return createLibrary(name, randomCode(), campusId);
  }

  public IndividualResource createLibrary(String name, String code, UUID campusId) {
    final JsonObject library = buildLocationUnitTemplate(name, code);
    write(library, "campusId", campusId);

    return libraryRecordCreator.createIfAbsent(library);
  }

  public IndividualResource createLocation(LocationBuilder locationBuilder) {
    return locationRecordCreator.createIfAbsent(locationBuilder);
  }

  private static JsonObject buildLocationUnitTemplate(String name, String code) {
    final JsonObject locationUnit = new JsonObject();
    write(locationUnit, "name", name);
    write(locationUnit, "code", code);

    return locationUnit;
  }

  private static String randomCode() {
    return String.valueOf(new Random().nextInt());
  }
}
