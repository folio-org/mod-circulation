package api.support.fixtures;

import api.support.http.ResourceClient;
import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.folio.circulation.support.http.client.IndividualResource;

public class ServicePointsFixture {
  private final ResourceClient servicePointsClient;
  
  public ServicePointsFixture(ResourceClient client) {
    servicePointsClient = client;
  }
  
  public IndividualResource cd1() 
      throws InterruptedException, 
      MalformedURLException, 
      TimeoutException, 
      ExecutionException {
    return servicePointsClient.create(ServicePointExamples.basedUponCircDesk1());
  }
  
  public IndividualResource cd2() 
      throws InterruptedException, 
      MalformedURLException, 
      TimeoutException, 
      ExecutionException {
    return servicePointsClient.create(ServicePointExamples.basedUponCircDesk2());
  } 
  
  public IndividualResource cd3() 
      throws InterruptedException, 
      MalformedURLException, 
      TimeoutException, 
      ExecutionException {
    return servicePointsClient.create(ServicePointExamples.basedUponCircDesk3());
  } 
}
