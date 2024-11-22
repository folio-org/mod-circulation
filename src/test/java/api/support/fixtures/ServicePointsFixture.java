package api.support.fixtures;

import static api.support.fixtures.ServicePointExamples.basedUponCircDesk1;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk10;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk11;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk2;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk3;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk4;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk5;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk6;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk7;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk8;
import static api.support.fixtures.ServicePointExamples.basedUponCircDesk9;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.Collection;

import api.support.builders.ServicePointBuilder;
import api.support.http.IndividualResource;
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

  public IndividualResource cd7() {

    return create(basedUponCircDesk7());
  }

  public IndividualResource cd8() {

    return create(basedUponCircDesk8());
  }

  public IndividualResource cd9() {

    return create(basedUponCircDesk9());
  }

  public IndividualResource cd10() {

    return create(basedUponCircDesk10());
  }

  public IndividualResource cd11() {

    return create(basedUponCircDesk11());
  }

  public IndividualResource create(ServicePointBuilder builder) {

    return servicePointRecordCreator.createIfAbsent(builder);
  }

  public Collection<IndividualResource> getAllServicePoints() {
    return servicePointRecordCreator.getIdentityMap().values();
  }

  public void update(String servicePointCode, ServicePointBuilder builder) {
    servicePointRecordCreator.update(servicePointCode, builder.create());
  }
}
