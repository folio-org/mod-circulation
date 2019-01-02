package api.support.fixtures;

import static java.util.function.Function.identity;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.OkapiHttpClient;

import api.support.builders.HoldingBuilder;
import api.support.builders.InstanceBuilder;
import api.support.builders.ItemBuilder;
import api.support.http.InventoryItemResource;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class ItemsFixture {
  private final ResourceClient itemsClient;
  private final ResourceClient holdingsClient;
  private final ResourceClient instancesClient;
  private final MaterialTypesFixture materialTypesFixture;
  private final LoanTypesFixture loanTypesFixture;
  private final LocationsFixture locationsFixture;
  private final RecordCreator instanceTypeRecordCreator;

  public ItemsFixture(
    OkapiHttpClient client,
    MaterialTypesFixture materialTypesFixture,
    LoanTypesFixture loanTypesFixture,
    LocationsFixture locationsFixture,
    ResourceClient instanceTypesClient) {

    itemsClient = ResourceClient.forItems(client);
    holdingsClient = ResourceClient.forHoldings(client);
    instancesClient = ResourceClient.forInstances(client);
    this.materialTypesFixture = materialTypesFixture;
    this.loanTypesFixture = loanTypesFixture;
    this.locationsFixture = locationsFixture;

    instanceTypeRecordCreator = new RecordCreator(instanceTypesClient,
      instanceType -> getProperty(instanceType, "name"));
  }

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    //TODO: Also clean up created instances, holdings record and items
    instanceTypeRecordCreator.cleanUp();
  }

  public InventoryItemResource basedUponDunkirk()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(
      InstanceExamples.basedUponDunkirk(booksInstanceTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponDunkirk(materialTypesFixture.videoRecording(),
        loanTypesFixture.canCirculate()));
  }

  public InventoryItemResource basedUponSmallAngryPlanet()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return basedUponSmallAngryPlanet(identity());
  }

  public InventoryItemResource basedUponSmallAngryPlanet(
    Function<ItemBuilder, ItemBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return basedUponSmallAngryPlanet(
      identity(),
      additionalItemProperties);
  }

  public InventoryItemResource basedUponSmallAngryPlanet(
    Function<HoldingBuilder, HoldingBuilder> additionalHoldingsRecordProperties,
    Function<ItemBuilder, ItemBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return applyAdditionalProperties(
      additionalHoldingsRecordProperties,
      additionalItemProperties,
      InstanceExamples.basedUponSmallAngryPlanet(booksInstanceTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponSmallAngryPlanet(materialTypesFixture.book(),
        loanTypesFixture.canCirculate()));
  }

  public InventoryItemResource basedUponNod()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return basedUponNod(identity());
  }

  public InventoryItemResource basedUponNod(
    Function<ItemBuilder, ItemBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return applyAdditionalProperties(
      identity(),
      additionalItemProperties,
      InstanceExamples.basedUponNod(booksInstanceTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponNod(materialTypesFixture.book(),
        loanTypesFixture.canCirculate()));
  }

  public InventoryItemResource basedUponTemeraire()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return basedUponTemeraire(identity());
  }

  public InventoryItemResource basedUponTemeraire(
    Function<HoldingBuilder, HoldingBuilder> additionalHoldingsRecordProperties,
    Function<ItemBuilder, ItemBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return applyAdditionalProperties(
      additionalHoldingsRecordProperties,
      additionalItemProperties,
      InstanceExamples.basedUponTemeraire(booksInstanceTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponTemeraire(materialTypesFixture.book(),
        loanTypesFixture.canCirculate()));
  }

  public InventoryItemResource basedUponTemeraire(
    Function<ItemBuilder, ItemBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return basedUponTemeraire(identity(), additionalItemProperties);
  }

  public InventoryItemResource basedUponUprooted()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return basedUponUprooted(identity());
  }

  public InventoryItemResource basedUponUprooted(
    Function<ItemBuilder, ItemBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return applyAdditionalProperties(
      identity(),
      additionalItemProperties,
      InstanceExamples.basedUponUprooted(booksInstanceTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponUprooted(materialTypesFixture.book(),
        loanTypesFixture.canCirculate()));
  }

  public InventoryItemResource basedUponInterestingTimes()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return basedUponInterestingTimes(identity());
  }

  public InventoryItemResource basedUponInterestingTimes(
    Function<ItemBuilder, ItemBuilder> additionalItemProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return applyAdditionalProperties(
      identity(),
      additionalItemProperties,
      InstanceExamples.basedUponInterestingTimes(booksInstanceTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponInterestingTimes(materialTypesFixture.book(),
        loanTypesFixture.canCirculate()));
  }

  private InventoryItemResource applyAdditionalProperties(
    Function<HoldingBuilder, HoldingBuilder> additionalHoldingsRecordProperties,
    Function<ItemBuilder, ItemBuilder> additionalItemProperties,
    InstanceBuilder instanceBuilder,
    HoldingBuilder holdingsRecordBuilder,
    ItemBuilder itemBuilder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(
      instanceBuilder,
      additionalHoldingsRecordProperties.apply(holdingsRecordBuilder),
      additionalItemProperties.apply(itemBuilder));
  }

  private InventoryItemResource create(
    InstanceBuilder instanceBuilder,
    HoldingBuilder holdingsRecordBuilder,
    ItemBuilder itemBuilder)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    IndividualResource instance = instancesClient.create(
      instanceBuilder.withInstanceTypeId(booksInstanceTypeId()));

    IndividualResource holding = holdingsClient.create(
      holdingsRecordBuilder.forInstance(instance.getId()));

    final IndividualResource item = itemsClient.create(
      itemBuilder.forHolding(holding.getId())
        .create());

    return new InventoryItemResource(item, holding, instance);
  }

  private HoldingBuilder thirdFloorHoldings()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return new HoldingBuilder()
      .withPermanentLocation(locationsFixture.thirdFloor())
      .withNoTemporaryLocation()
      .withCallNumber("123456");
  }

  private UUID booksInstanceTypeId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final JsonObject booksInstanceType = new JsonObject();

    write(booksInstanceType, "name", "Books");
    write(booksInstanceType, "code", "BO");
    write(booksInstanceType, "source", "tests");

    return instanceTypeRecordCreator.createIfAbsent(booksInstanceType).getId();
  }
}
