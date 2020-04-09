package api.support.fixtures;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.FeeFineOwnerBuilder;
import api.support.http.ResourceClient;

public final class FeeFineOwnerFixture {
  private final RecordCreator recordCreator;

  public FeeFineOwnerFixture(ResourceClient client) {
    this.recordCreator = new RecordCreator(client,
      json -> json.getJsonArray("servicePointOwner").toString());
  }

  public IndividualResource create(UUID servicePointId) {
    return recordCreator.createIfAbsent(new FeeFineOwnerBuilder()
      .withOwner("Test owner for cd1")
      .withServicePointOwner(servicePointId));
  }

  public void cleanUp() {
    recordCreator.cleanUp();
  }
}
