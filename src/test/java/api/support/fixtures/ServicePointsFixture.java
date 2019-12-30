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

import api.support.builders.ServicePointBuilder;
import api.support.http.ResourceClient;

public class ServicePointsFixture {
  private final RecordCreator servicePointRecordCreator;

  public ServicePointsFixture(ResourceClient client) {
    servicePointRecordCreator = new RecordCreator(client,
      servicePoint -> getProperty(servicePoint, "code"));
  }

  public void cleanUp() {

    servicePointRecordCreator.cleanUp();
  }

  public IndividualResource cd1() {

    return create(basedUponCircDesk1());
  }

  public IndividualResource cd2() {

    return create(basedUponCircDesk2());
  }

  public IndividualResource cd3() {

    return create(basedUponCircDesk3());
  }

  public IndividualResource cd4() {

    return create(basedUponCircDesk4());
  }

  public IndividualResource cd5() {

    return create(basedUponCircDesk5());
  }

  public IndividualResource cd6() {

    return create(basedUponCircDesk6());
  }

  public IndividualResource create(ServicePointBuilder builder) {

    return servicePointRecordCreator.createIfAbsent(builder);
  }
}
