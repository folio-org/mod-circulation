package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.APITestSuite;
import org.folio.circulation.api.support.builders.HoldingRequestBuilder;
import org.folio.circulation.api.support.builders.ItemRequestBuilder;
import org.folio.circulation.api.support.http.ResourceClient;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.OkapiHttpClient;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class ItemFixture {

  private final ResourceClient itemsClient;
  private final ResourceClient holdingsClient;
  private final ResourceClient instancesClient;
  private final UUID defaultPermanentLocation;

  public ItemFixture(OkapiHttpClient client) {
    itemsClient = ResourceClient.forItems(client);
    holdingsClient = ResourceClient.forHoldings(client);
    instancesClient = ResourceClient.forInstances(client);
    defaultPermanentLocation = APITestSuite.mainLibraryLocationId();
  }

  public IndividualResource basedUponSmallAngryPlanet(
    Consumer<ItemRequestBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource instance = instancesClient.create(
      InstanceRequestExamples.smallAngryPlanet().create());

    HoldingRequestBuilder holdingBuilder = new HoldingRequestBuilder(instance.getId())
      .withPermanentLocation(defaultPermanentLocation);

    IndividualResource holding = holdingsClient.create(holdingBuilder.create());

    ItemRequestBuilder builder = ItemRequestExamples.basedUponSmallAngryPlanet();

    builder.forHolding(holding.getId());

    additionalItemProperties.accept(builder);

    return itemsClient.create(builder.create());
  }
}
