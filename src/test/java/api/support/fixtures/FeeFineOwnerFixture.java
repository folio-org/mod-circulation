package api.support.fixtures;

import static api.support.http.ResourceClient.forFeeFineOwners;
import static api.support.http.ResourceClient.forServicePoints;

import java.util.UUID;

import api.support.http.IndividualResource;

import api.support.builders.FeeFineOwnerBuilder;

public final class FeeFineOwnerFixture extends RecordCreator {
  private final ServicePointsFixture servicePointsFixture;

  public FeeFineOwnerFixture() {
    super(forFeeFineOwners(), json -> json.getString("owner"));
    this.servicePointsFixture = new ServicePointsFixture(forServicePoints());
  }

  public IndividualResource cd1Owner() {
    return createIfAbsent(new FeeFineOwnerBuilder()
      .withOwner("Test owner for cd1")
      .withServicePointOwner(servicePointsFixture.cd1().getId()));
  }

  public IndividualResource ownerForServicePoint(UUID servicePointId) {
    return createIfAbsent(new FeeFineOwnerBuilder()
      .withOwner("Test owner for service point" + servicePointId.toString())
      .withServicePointOwner(servicePointId));
  }

  public IndividualResource create(FeeFineOwnerBuilder builder) {
    return createIfAbsent(builder);
  }
}
