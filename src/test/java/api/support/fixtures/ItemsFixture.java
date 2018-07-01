package api.support.fixtures;

import api.support.builders.HoldingBuilder;
import api.support.builders.InstanceBuilder;
import api.support.builders.ItemBuilder;
import api.support.http.ResourceClient;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.OkapiHttpClient;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static api.APITestSuite.booksInstanceTypeId;
import static api.APITestSuite.thirdFloorLocationId;

public class ItemsFixture {

  private final ResourceClient itemsClient;
  private final ResourceClient holdingsClient;
  private final ResourceClient instancesClient;

  public ItemsFixture(OkapiHttpClient client) {
    itemsClient = ResourceClient.forItems(client);
    holdingsClient = ResourceClient.forHoldings(client);
    instancesClient = ResourceClient.forInstances(client);
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
      instanceBuilder.withInstanceTypeId(booksInstanceTypeId()));

    IndividualResource holding = holdingsClient.create(new HoldingBuilder()
      .forInstance(instance.getId())
      .withPermanentLocation(thirdFloorLocationId())
      .withNoTemporaryLocation()
      .withCallNumber("123456"));

    return itemsClient.create(
      itemBuilder.forHolding(holding.getId())
      .create());
  }
}
