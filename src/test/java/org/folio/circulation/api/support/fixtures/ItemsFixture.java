package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.APITestSuite;
import org.folio.circulation.api.support.builders.HoldingRequestBuilder;
import org.folio.circulation.api.support.builders.InstanceRequestBuilder;
import org.folio.circulation.api.support.builders.ItemRequestBuilder;
import org.folio.circulation.api.support.http.ResourceClient;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.OkapiHttpClient;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public class ItemsFixture {

  private final ResourceClient itemsClient;
  private final ResourceClient holdingsClient;
  private final ResourceClient instancesClient;
  private final UUID defaultPermanentLocation;

  public ItemsFixture(OkapiHttpClient client) {
    itemsClient = ResourceClient.forItems(client);
    holdingsClient = ResourceClient.forHoldings(client);
    instancesClient = ResourceClient.forInstances(client);
    defaultPermanentLocation = APITestSuite.mainLibraryLocationId();
  }

  public IndividualResource basedUponDunkirk()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(
      InstanceRequestExamples.basedUponDunkirk(),
      ItemRequestExamples.basedUponDunkirk());
  }

  public IndividualResource basedUponSmallAngryPlanet()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(
      InstanceRequestExamples.basedUponSmallAngryPlanet(),
      ItemRequestExamples.basedUponSmallAngryPlanet());
  }

  public IndividualResource basedUponSmallAngryPlanet(
    Function<ItemRequestBuilder, ItemRequestBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return applyAdditionalProperties(
      additionalItemProperties,
      InstanceRequestExamples.basedUponSmallAngryPlanet(),
      ItemRequestExamples.basedUponSmallAngryPlanet());
  }

  public IndividualResource basedUponNod()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(
      InstanceRequestExamples.basedUponNod(),
      ItemRequestExamples.basedUponNod());
  }

  public IndividualResource basedUponNod(
    Function<ItemRequestBuilder, ItemRequestBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return applyAdditionalProperties(
      additionalItemProperties,
      InstanceRequestExamples.basedUponNod(),
      ItemRequestExamples.basedUponNod());
  }

  public IndividualResource basedUponTemeraire()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(
      InstanceRequestExamples.basedUponTemeraire(),
      ItemRequestExamples.basedUponTemeraire());
  }

  public IndividualResource basedUponTemeraire(
    Function<ItemRequestBuilder, ItemRequestBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return applyAdditionalProperties(
      additionalItemProperties,
      InstanceRequestExamples.basedUponTemeraire(),
      ItemRequestExamples.basedUponTemeraire());
  }

  public IndividualResource basedUponUprooted()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(
      InstanceRequestExamples.basedUponUprooted(),
      ItemRequestExamples.basedUponUprooted());
  }

  public IndividualResource basedUponUprooted(
    Function<ItemRequestBuilder, ItemRequestBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return applyAdditionalProperties(
      additionalItemProperties,
      InstanceRequestExamples.basedUponUprooted(),
      ItemRequestExamples.basedUponUprooted());
  }

  public IndividualResource basedUponInterestingTimes()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(
      InstanceRequestExamples.basedUponInterestingTimes(),
      ItemRequestExamples.basedUponInterestingTimes());
  }

  public IndividualResource basedUponInterestingTimes(
    Function<ItemRequestBuilder, ItemRequestBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return applyAdditionalProperties(
      additionalItemProperties,
      InstanceRequestExamples.basedUponInterestingTimes(),
      ItemRequestExamples.basedUponInterestingTimes());
  }

  private IndividualResource applyAdditionalProperties(
    Function<ItemRequestBuilder, ItemRequestBuilder> additionalItemProperties,
    InstanceRequestBuilder instanceRequestBuilder,
    ItemRequestBuilder itemRequestBuilder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(
      instanceRequestBuilder,
      additionalItemProperties.apply(itemRequestBuilder));
  }

  private IndividualResource create(
    InstanceRequestBuilder instanceRequestBuilder,
    ItemRequestBuilder itemRequestBuilder)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    IndividualResource instance = instancesClient.create(
      instanceRequestBuilder);

    HoldingRequestBuilder holdingBuilder = new HoldingRequestBuilder()
      .forInstance(instance.getId())
      .withPermanentLocation(defaultPermanentLocation);

    IndividualResource holding = holdingsClient.create(holdingBuilder);

    return itemsClient.create(
      itemRequestBuilder.forHolding(holding.getId())
      .create());
  }
}
