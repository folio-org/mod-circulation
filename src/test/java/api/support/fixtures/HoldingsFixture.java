package api.support.fixtures;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.Item;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.OkapiHttpClient;

import api.support.builders.HoldingBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonArray;

public class HoldingsFixture {
  private final ResourceClient holdingsClient;

  public HoldingsFixture(OkapiHttpClient client) {
    holdingsClient =  ResourceClient.forHoldings(client);
  }

  public IndividualResource defaultWithHoldings(UUID instanceId)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {


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
