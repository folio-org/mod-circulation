package api.support.fixtures;

import static java.util.function.Function.identity;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.net.MalformedURLException;
import java.util.List;
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
  private final RecordCreator contributorNameTypeRecordCreator;

  public ItemsFixture(
    OkapiHttpClient client,
    MaterialTypesFixture materialTypesFixture,
    LoanTypesFixture loanTypesFixture,
    LocationsFixture locationsFixture,
    ResourceClient instanceTypesClient,
    ResourceClient contributorNameTypesClient) {

    itemsClient = ResourceClient.forItems(client);
    holdingsClient = ResourceClient.forHoldings(client);
    instancesClient = ResourceClient.forInstances(client);
    this.materialTypesFixture = materialTypesFixture;
    this.loanTypesFixture = loanTypesFixture;
    this.locationsFixture = locationsFixture;

    instanceTypeRecordCreator = new RecordCreator(instanceTypesClient,
      instanceType -> getProperty(instanceType, "name"));

    contributorNameTypeRecordCreator = new RecordCreator(
      contributorNameTypesClient, nameType -> getProperty(nameType, "name"));
  }

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    //TODO: Also clean up created instances, holdings record and items
    instanceTypeRecordCreator.cleanUp();
    contributorNameTypeRecordCreator.cleanUp();
  }

  public InventoryItemResource basedUponDunkirk()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(
      InstanceExamples.basedUponDunkirk(booksInstanceTypeId(),
        getPersonalContributorNameTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponDunkirk(materialTypesFixture.videoRecording().getId(),
        loanTypesFixture.canCirculate().getId()));
  }

  public IndividualResource basedUponDunkirkWithCustomHoldingAndLocation(UUID holdingsId, UUID locationId)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    JsonObject item1 = ItemExamples.basedUponDunkirk(UUID.randomUUID(), loanTypesFixture.canCirculate().getId())
      .forHolding(holdingsId)
      .available()
      .withTemporaryLocation(locationId)
      .withMaterialType(materialTypesFixture.videoRecording().getId())
      .create();

    return itemsClient.create(item1);
  }

  public IndividualResource basedUponDunkirkWithCustomHoldingAndLocationAndCheckedOut(UUID holdingsId, UUID locationId)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    JsonObject item1 = ItemExamples.basedUponDunkirk(UUID.randomUUID(), loanTypesFixture.canCirculate().getId())
      .forHolding(holdingsId)
      .checkOut()
      .withTemporaryLocation(locationId)
      .create();

    return itemsClient.create(item1);
  }
  public IndividualResource basedUponDunkirkWithCustomHoldingAndLocationAndStatusInProcess(UUID holdingsId, UUID locationId)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    JsonObject item1 = ItemExamples.basedUponDunkirk(UUID.randomUUID(), loanTypesFixture.canCirculate().getId())
      .forHolding(holdingsId)
      .inProcess()
      .withTemporaryLocation(locationId)
      .create();

    return itemsClient.create(item1);
  }

  public InventoryItemResource basedUponSmallAngryPlanet()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return basedUponSmallAngryPlanet(identity());
  }

  public InventoryItemResource basedUponSmallAngryPlanet(String barcode)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return basedUponSmallAngryPlanet(item -> item.withBarcode(barcode));
  }

  public InventoryItemResource basedUponSmallAngryPlanet(
    ItemBuilder itemBuilder,
    HoldingBuilder holdingBuilder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return applyAdditionalProperties(
      identity(),
      identity(),
      InstanceExamples.basedUponSmallAngryPlanet(booksInstanceTypeId(),
        getPersonalContributorNameTypeId()),
      holdingBuilder,
      itemBuilder);
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
      InstanceExamples.basedUponSmallAngryPlanet(booksInstanceTypeId(),
        getPersonalContributorNameTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponSmallAngryPlanet(materialTypesFixture.book().getId(),
        loanTypesFixture.canCirculate().getId()));
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
      InstanceExamples.basedUponNod(booksInstanceTypeId(),
        getPersonalContributorNameTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponNod(materialTypesFixture.book().getId(),
        loanTypesFixture.canCirculate().getId()));
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
      InstanceExamples.basedUponTemeraire(booksInstanceTypeId(),
        getPersonalContributorNameTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponTemeraire(materialTypesFixture.book().getId(),
        loanTypesFixture.canCirculate().getId()));
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
      InstanceExamples.basedUponUprooted(booksInstanceTypeId(),
        getPersonalContributorNameTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponUprooted(materialTypesFixture.book().getId(),
        loanTypesFixture.canCirculate().getId()));
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
      InstanceExamples.basedUponInterestingTimes(booksInstanceTypeId(),
        getPersonalContributorNameTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponInterestingTimes(materialTypesFixture.book().getId(),
        loanTypesFixture.canCirculate().getId()));
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

  public HoldingBuilder thirdFloorHoldings()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return new HoldingBuilder()
      .withPermanentLocation(locationsFixture.thirdFloor())
      .withNoTemporaryLocation()
      .withCallNumber("123456");
  }

  public HoldingBuilder applyCallNumberHoldings(
    String callNumber,
    String callNumberPrefix,
    String callNumberSuffix,
    List<String> copyNumbers)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return new HoldingBuilder()
      .withPermanentLocation(locationsFixture.thirdFloor())
      .withNoTemporaryLocation()
      .withCallNumber(callNumber)
      .withCallNumberPrefix(callNumberPrefix)
      .withCallNumberSuffix(callNumberSuffix)
      .withCopyNumbers(copyNumbers);
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

  private UUID getPersonalContributorNameTypeId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final JsonObject personalName = new JsonObject();

    write(personalName, "name", "Personal name");

    return contributorNameTypeRecordCreator.createIfAbsent(personalName).getId();
  }
}
