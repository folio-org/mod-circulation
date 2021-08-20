package api.support.fixtures;

import java.util.UUID;

import api.support.builders.HoldingBuilder;
import api.support.http.IndividualResource;
import api.support.http.ResourceClient;

class HoldingsFixture {
  private final ResourceClient holdingsClient;

  public HoldingsFixture() {
    holdingsClient =  ResourceClient.forHoldings();
  }

  public IndividualResource defaultWithHoldings(UUID instanceId) {


    HoldingBuilder holdingsBuilder = new HoldingBuilder()
                    .forInstance(instanceId)
                    .withPermanentLocation(UUID.randomUUID());
    IndividualResource holdingsResource = holdingsClient.create(holdingsBuilder);

    return holdingsResource;
  }
}
