package api.support.fixtures;

import static api.APITestSuite.djanoglyLibrary;
import static api.APITestSuite.fakeServicePoint;
import static api.APITestSuite.jubileeCampus;
import static api.APITestSuite.nottinghamUniversityInstitution;

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
    locationRecordCreator = new RecordCreator(client);
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

    return locationRecordCreator.create(
      additionalLocationProperties.apply(exampleLocation()));
  }

  private LocationBuilder exampleLocation() {
    return new LocationBuilder()
      .withName("Example location")
      .withCode("NU/JC/DL/EX")
      .forInstitution(nottinghamUniversityInstitution())
      .forCampus(jubileeCampus())
      .forLibrary(djanoglyLibrary())
      .withPrimaryServicePoint(fakeServicePoint());
  }
}
