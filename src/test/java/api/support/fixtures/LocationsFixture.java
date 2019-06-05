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

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    locationRecordCreator.cleanUp();

    libraryRecordCreator.cleanUp();
    campusRecordCreator.cleanUp();
    institutionRecordCreator.cleanUp();
  }

  public IndividualResource basedUponExampleLocation(
    Function<LocationBuilder, LocationBuilder> additionalLocationProperties)
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    final LocationExamples locationExamples = getLocationExamples();

    return locationRecordCreator.createIfAbsent(
      additionalLocationProperties.apply(locationExamples.example()));
  }

  public IndividualResource thirdFloor()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final LocationExamples locationExamples = getLocationExamples();

    return locationRecordCreator.createIfAbsent(
      locationExamples.thirdFloor());
  }

  public IndividualResource secondFloorEconomics()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final LocationExamples locationExamples = getLocationExamples();

    return locationRecordCreator.createIfAbsent(
      locationExamples.secondFloorEconomics());
  }

  public IndividualResource mezzanineDisplayCase()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

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
  public IndividualResource mainFloor()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final LocationExamples locationExamples = getLocationMultiServicePointsExamples();

    return locationRecordCreator.createIfAbsent(locationExamples.mainLocation());
  }

  private LocationExamples getLocationExamples()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return new LocationExamples(
      nottinghamUniversity().getId(),
      jubileeCampus().getId(),
      businessLibrary().getId(),
      djanoglyLibrary().getId(),
      servicePointsFixture.cd1().getId(),
      null);
  }

  private LocationExamples getLocationMultiServicePointsExamples()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return new LocationExamples(
      nottinghamUniversity().getId(),
      jubileeCampus().getId(),
      businessLibrary().getId(),
      djanoglyLibrary().getId(),
      servicePointsFixture.cd1().getId(),
      null);
  }

  private IndividualResource djanoglyLibrary()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final JsonObject djanoglyLibrary = new JsonObject();

    write(djanoglyLibrary, "name", "Djanogly Learning Resource Centre");
    write(djanoglyLibrary, "campusId", jubileeCampus().getId());

    return libraryRecordCreator.createIfAbsent(djanoglyLibrary);
  }

  private IndividualResource businessLibrary()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final JsonObject businessLibrary = new JsonObject();

    write(businessLibrary, "name", "Business Library");
    write(businessLibrary, "campusId", jubileeCampus().getId());

    return libraryRecordCreator.createIfAbsent(businessLibrary);
  }

  private IndividualResource jubileeCampus()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final JsonObject jubileeCampus = new JsonObject();

    write(jubileeCampus, "name", "Jubilee Campus");
    write(jubileeCampus, "institutionId", nottinghamUniversity().getId());

    return campusRecordCreator.createIfAbsent(jubileeCampus);
  }

  private IndividualResource nottinghamUniversity()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final JsonObject nottinghamUniversity = new JsonObject();

    write(nottinghamUniversity, "name", "Nottingham University");

    return institutionRecordCreator.createIfAbsent(nottinghamUniversity);
  }
}
