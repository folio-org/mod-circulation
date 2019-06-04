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
import io.vertx.core.json.JsonObject;

public class HoldingsFixture {
  private final ResourceClient holdingsClient;
  private final ItemsFixture itemsFixture;
  private final ResourceClient itemsClient;
  private final LoanTypesFixture loanTypesFixture;
  private final LocationsFixture locationsFixture;

  public HoldingsFixture(OkapiHttpClient client, ItemsFixture itemsFixture, LoanTypesFixture loanTypesFixture, LocationsFixture locationsFixture) {
    holdingsClient =  ResourceClient.forHoldings(client);
    this.itemsFixture = itemsFixture;
    this.itemsClient = ResourceClient.forItems(client);
    this.loanTypesFixture = loanTypesFixture;
    this.locationsFixture = locationsFixture;
  }

  public IndividualResource customHoldings(UUID instanceId, List<Item> holdingsItems)
      throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {
    HoldingBuilder holdingBuilder = new HoldingBuilder();
    holdingBuilder.forInstance(instanceId);
    holdingBuilder.withCallNumber("single1234");
    holdingBuilder.withHoldingsItems(getJsonHoldingItems(holdingsItems));

    return holdingsClient.create(holdingBuilder.create());
  }

  public IndividualResource defaultWithHoldings(UUID instanceId)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {


    HoldingBuilder holdingsBuilder = new HoldingBuilder()
                    .forInstance(instanceId)
                    .withPermanentLocation(UUID.randomUUID());
    IndividualResource holdingsResource = holdingsClient.create(holdingsBuilder);

    /*
    JsonArray holdingsIems = new JsonArray();
    holdingsIems.add(item1);
    holdingsIems.add(item2);

    JsonObject holdingsRecordJson = holdingsResource.getJson();
    holdingsRecordJson.put("holdingsItems", holdingsIems);

    holdingsClient.replace(holdingsResource.getId(),holdingsRecordJson );
    */
   // itemsClient.getByQueryString("query=holdingsRecordId=" + holdingsRecordJson.getString("id"));


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
