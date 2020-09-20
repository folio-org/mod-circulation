package api.support.fixtures;

import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.Item;
import api.support.http.IndividualResource;

import api.support.builders.HoldingBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonArray;

public class HoldingsFixture {
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

  private JsonArray getJsonHoldingItems(List<Item> holdingsItems) {
    JsonArray holdingsItemsJArray = new JsonArray();
    if (holdingsItems!= null && !holdingsItems.isEmpty()) {
      for (Item item: holdingsItems) {
       holdingsItemsJArray.add(item.getItem());
      }
    }
    return holdingsItemsJArray;
  }
}
