package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.APITestSuite;
import org.folio.circulation.api.support.builders.HoldingBuilder;
import org.folio.circulation.api.support.builders.InstanceBuilder;
import org.folio.circulation.api.support.builders.ItemBuilder;
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
      InstanceExamples.basedUponDunkirk(),
      ItemExamples.basedUponDunkirk());
  }

  public IndividualResource basedUponSmallAngryPlanet()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(
      InstanceExamples.basedUponSmallAngryPlanet(),
      ItemExamples.basedUponSmallAngryPlanet());
  }

  public IndividualResource basedUponSmallAngryPlanet(
    Function<ItemBuilder, ItemBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return applyAdditionalProperties(
      additionalItemProperties,
      InstanceExamples.basedUponSmallAngryPlanet(),
      ItemExamples.basedUponSmallAngryPlanet());
  }

  public IndividualResource basedUponNod()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(
      InstanceExamples.basedUponNod(),
      ItemExamples.basedUponNod());
  }

  public IndividualResource basedUponNod(
    Function<ItemBuilder, ItemBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return applyAdditionalProperties(
      additionalItemProperties,
      InstanceExamples.basedUponNod(),
      ItemExamples.basedUponNod());
  }

  public IndividualResource basedUponTemeraire()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(
      InstanceExamples.basedUponTemeraire(),
      ItemExamples.basedUponTemeraire());
  }

  public IndividualResource basedUponTemeraire(
    Function<ItemBuilder, ItemBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return applyAdditionalProperties(
      additionalItemProperties,
      InstanceExamples.basedUponTemeraire(),
      ItemExamples.basedUponTemeraire());
  }

  public IndividualResource basedUponUprooted()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(
      InstanceExamples.basedUponUprooted(),
      ItemExamples.basedUponUprooted());
  }

  public IndividualResource basedUponUprooted(
    Function<ItemBuilder, ItemBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return applyAdditionalProperties(
      additionalItemProperties,
      InstanceExamples.basedUponUprooted(),
      ItemExamples.basedUponUprooted());
  }

  public IndividualResource basedUponInterestingTimes()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(
      InstanceExamples.basedUponInterestingTimes(),
      ItemExamples.basedUponInterestingTimes());
  }

  public IndividualResource basedUponInterestingTimes(
    Function<ItemBuilder, ItemBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return applyAdditionalProperties(
      additionalItemProperties,
      InstanceExamples.basedUponInterestingTimes(),
      ItemExamples.basedUponInterestingTimes());
  }

  private IndividualResource applyAdditionalProperties(
    Function<ItemBuilder, ItemBuilder> additionalItemProperties,
    InstanceBuilder instanceBuilder,
    ItemBuilder itemBuilder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(
      instanceBuilder,
      additionalItemProperties.apply(itemBuilder));
  }

  private IndividualResource create(
    InstanceBuilder instanceBuilder,
    ItemBuilder itemBuilder)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    IndividualResource instance = instancesClient.create(
      instanceBuilder);

    HoldingBuilder holdingBuilder = new HoldingBuilder()
      .forInstance(instance.getId())
      .withPermanentLocation(defaultPermanentLocation);

    IndividualResource holding = holdingsClient.create(holdingBuilder.withCallNumber("123456"));

    return itemsClient.create(
      itemBuilder.forHolding(holding.getId())
      .create());
  }
}
