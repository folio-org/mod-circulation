package api.support.fixtures;

import static api.APITestSuite.fakeServicePoint;
import static api.support.examples.LocationExamples.exampleLocation;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.LocationBuilder;
import api.support.http.ResourceClient;

public class LocationsFixture {
  private final RecordCreator locationRecordCreator;

  public LocationsFixture(ResourceClient client) {
    locationRecordCreator = new RecordCreator(client,
      location -> getProperty(location, "code"));
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
      additionalLocationProperties.apply(exampleLocation(fakeServicePoint())));
  }
}
