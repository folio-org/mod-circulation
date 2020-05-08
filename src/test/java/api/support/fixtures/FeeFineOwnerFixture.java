package api.support.fixtures;

import static api.support.http.ResourceClient.forFeeFineOwners;
import static api.support.http.ResourceClient.forServicePoints;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.FeeFineOwnerBuilder;

public final class FeeFineOwnerFixture extends RecordCreator {
  private final ServicePointsFixture servicePointsFixture;

  public FeeFineOwnerFixture() {
    super(forFeeFineOwners(), json -> json.getJsonArray("servicePointOwner").toString());
    this.servicePointsFixture = new ServicePointsFixture(forServicePoints());
  }

  public IndividualResource cd1Owner() {
    return createIfAbsent(new FeeFineOwnerBuilder()
      .withOwner("Test owner for cd1")
      .withServicePointOwner(servicePointsFixture.cd1().getId()));
  }
}
