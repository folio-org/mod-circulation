package api.support.fixtures;

import static api.support.examples.LocationExamples.exampleLocation;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.LocationBuilder;
import api.support.examples.LocationExamples;
import api.support.http.ResourceClient;

public class LocationsFixture {
  private final RecordCreator locationRecordCreator;
  private final ServicePointsFixture servicePointsFixture;

  public LocationsFixture(
    ResourceClient client,
    ServicePointsFixture servicePointsFixture) {

    this.locationRecordCreator = new RecordCreator(client,
      location -> getProperty(location, "code"));

    this.servicePointsFixture = servicePointsFixture;
  }

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    locationRecordCreator.cleanUp();
  }

  public IndividualResource basedUponExampleLocation(
    Function<LocationBuilder, LocationBuilder> additionalLocationProperties)
      throws InterruptedException, 
      MalformedURLException, 
      TimeoutException, 
      ExecutionException {

    return locationRecordCreator.createIfAbsent(
      additionalLocationProperties.apply(exampleLocation(
        servicePointsFixture.fake().getId())));
  }

  public IndividualResource thirdFloor()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return locationRecordCreator.createIfAbsent(
      LocationExamples.thirdFloor(
        servicePointsFixture.fake().getId()));
  }

  public IndividualResource secondFloorEconomics()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return locationRecordCreator.createIfAbsent(
      LocationExamples.secondFloorEconomics(
        servicePointsFixture.fake().getId()));
  }

  public IndividualResource mezzanineDisplayCase()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return locationRecordCreator.createIfAbsent(
      LocationExamples.mezzanineDisplayCase(
        servicePointsFixture.fake().getId()));
  }

}
