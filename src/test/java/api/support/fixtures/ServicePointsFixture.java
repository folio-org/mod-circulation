package api.support.fixtures;

import static api.support.fixtures.ServicePointExamples.basedUponCircDesk1;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk2;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk3;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk4;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk5;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk6;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.http.ResourceClient;

public class ServicePointsFixture {
  private final RecordCreator servicePointRecordCreator;
  
  public ServicePointsFixture(ResourceClient client) {
    servicePointRecordCreator = new RecordCreator(client,
      servicePoint -> getProperty(servicePoint, "code"));
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

    return servicePointRecordCreator.createIfAbsent(basedUponCircDesk1());
  }
  
  public IndividualResource cd2()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    return servicePointRecordCreator.createIfAbsent(basedUponCircDesk2());
  } 
  
  public IndividualResource cd3()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    return servicePointRecordCreator.createIfAbsent(basedUponCircDesk3());
  }

  public IndividualResource cd4()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    return servicePointRecordCreator.createIfAbsent(basedUponCircDesk4());
  }

  public IndividualResource cd5()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    return servicePointRecordCreator.createIfAbsent(basedUponCircDesk5());
  }

  public IndividualResource cd6()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    return servicePointRecordCreator.createIfAbsent(basedUponCircDesk6());
  }
}
