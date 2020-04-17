package api.support.fixtures;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.FeeFineOwnerBuilder;
import api.support.http.ResourceClient;

public final class FeeFineOwnerFixture extends RecordCreator {
  private final ServicePointsFixture servicePointsFixture;

  public FeeFineOwnerFixture(ResourceClient client, ServicePointsFixture servicePointsFixture) {
    super(client, json -> json.getJsonArray("servicePointOwner").toString());
    this.servicePointsFixture = servicePointsFixture;
  }

  public IndividualResource cd1Owner() {
    return createIfAbsent(new FeeFineOwnerBuilder()
      .withOwner("Test owner for cd1")
      .withServicePointOwner(servicePointsFixture.cd1().getId()));
  }
}
