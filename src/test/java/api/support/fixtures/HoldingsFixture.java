package api.support.fixtures;

import java.util.UUID;

import api.support.builders.HoldingBuilder;
import api.support.http.IndividualResource;
import api.support.http.ResourceClient;

public class HoldingsFixture {
  private final ResourceClient holdingsClient;

  public HoldingsFixture() {
    holdingsClient =  ResourceClient.forHoldings();
  }

  public IndividualResource defaultWithHoldings(UUID instanceId) {
    return createHoldingsRecord(instanceId, UUID.randomUUID());
  }

  public IndividualResource createHoldingsRecord(UUID instanceId, UUID permanentLocationId) {
    HoldingBuilder holdingsBuilder = new HoldingBuilder()
      .forInstance(instanceId)
      .withPermanentLocation(permanentLocationId);

    return holdingsClient.create(holdingsBuilder);
  }

  public IndividualResource createHoldingsRecord(HoldingBuilder holdingsBuilder) {
    return holdingsClient.create(holdingsBuilder);
  }
}
