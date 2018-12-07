package api.support.fixtures;

import static api.support.fixtures.ServicePointExamples.basedUponCircDesk1;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk2;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk3;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.http.ResourceClient;

public class ServicePointsFixture {
  private final RecordCreator servicePointRecordCreator;
  
  public ServicePointsFixture(ResourceClient client) {
    servicePointRecordCreator = new RecordCreator(client);
  }

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    servicePointRecordCreator.cleanUp();
  }

  public IndividualResource cd1() 
      throws InterruptedException, 
      MalformedURLException, 
      TimeoutException, 
      ExecutionException {
    return servicePointRecordCreator.create(basedUponCircDesk1());
  }
  
  public IndividualResource cd2() 
      throws InterruptedException, 
      MalformedURLException, 
      TimeoutException, 
      ExecutionException {
    return servicePointRecordCreator.create(basedUponCircDesk2());
  } 
  
  public IndividualResource cd3() 
      throws InterruptedException, 
      MalformedURLException, 
      TimeoutException, 
      ExecutionException {
    return servicePointRecordCreator.create(basedUponCircDesk3());
  } 
}
